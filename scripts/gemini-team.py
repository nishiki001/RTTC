#!/usr/bin/env python3
"""Two-tier Gemini run: Neon plans, Hydrogen workers edit in parallel, Neon reviews.

Usage: scripts/gemini-team.py <task-file> [--workers N] [--lead MODEL] [--worker-model MODEL] [--ref-dir DIR ...]

1. Plan:   Neon (plan mode, no edits) splits the spec into subtasks via --json-schema. Every subtask
           owns a disjoint set of files; the script rejects overlapping or out-of-repo paths.
2. Work:   One Hydrogen worker per subtask, up to N in parallel (default and cap 4). Each runs in its own
           detached git worktree; afterwards ONLY its owned files are copied back into the repo.
           Anything else it touched is logged as a violation and discarded.
3. Review: Neon checks the combined result against the original spec, may fix small issues inside
           the owned files, and returns {status, fixed, unresolved}. Edits outside the owned files are
           reverted and logged (only what git status sees; gitignored paths are not checked).
Workers whose agy run fails or times out have their output dropped, not copied back.

Builds, review by Claude and commit/revert stay with the caller (gemini-delegate).
Log: .gemini-tasks/<task-name>.log (human-readable, appended per run). Exit code 0 = all subtasks
ran and Neon reported status "ok"; 2 = finished with problems (see log); 1 = refused/aborted.
"""
from __future__ import annotations

import argparse
import concurrent.futures as cf
import datetime as dt
import json
import shutil
import subprocess
import sys
import tempfile
import threading
from pathlib import Path, PurePosixPath

LEAD = "gemini-4-neon-high"
WORKER = "gemini-4-hydrogen-medium"
TIMEOUT = "20m"
HARD_TIMEOUT_S = 25 * 60  # backstop if --print-timeout does not fire
MAX_WORKERS = 4
FORBIDDEN_TOP = {"build", "run", ".gradle", ".git", ".gemini-tasks"}
FORBIDDEN_PREFIXES = ("gradle/wrapper/",)

PLAN_SCHEMA = {
    "type": "object",
    "properties": {
        "subtasks": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "id": {"type": "string"},
                    "files": {"type": "array", "items": {"type": "string"}},
                    "spec": {"type": "string"},
                },
                "required": ["id", "files", "spec"],
            },
        }
    },
    "required": ["subtasks"],
}
REVIEW_SCHEMA = {
    "type": "object",
    "properties": {
        "status": {"type": "string", "enum": ["ok", "issues"]},
        "fixed": {"type": "array", "items": {"type": "string"}},
        "unresolved": {"type": "array", "items": {"type": "string"}},
    },
    "required": ["status", "fixed", "unresolved"],
}

RULES = """Rules: work only inside this repository (reference directories, if any, are read-only). Follow CLAUDE.md. Use file read/edit tools only: shell
commands are auto-denied in this mode (do not run gradle, git or validators). Do not commit."""

REF_DIRS: list[str] = []  # read-only reference dirs (outside git), added to every agy workspace
_log_lock = threading.Lock()
_git_lock = threading.Lock()


class Log:
    def __init__(self, path: Path):
        self.path = path
        path.parent.mkdir(parents=True, exist_ok=True)

    def write(self, text: str) -> None:
        with _log_lock, self.path.open("a", encoding="utf-8") as f:
            f.write(text.rstrip("\n") + "\n")
        print(text, flush=True)


def git(*args: str, cwd: Path) -> str:
    return subprocess.run(["git", *args], cwd=cwd, check=True, text=True, capture_output=True).stdout


def agy(prompt: str, model: str, mode: str, cwd: Path, schema: dict | None = None) -> dict:
    cmd = ["agy", "-p", prompt, "--model", model, "--mode", mode,
           "--print-timeout", TIMEOUT, "--output-format", "json"]
    if schema is not None:
        cmd += ["--json-schema", json.dumps(schema)]
    for d in REF_DIRS:
        cmd += ["--add-dir", d]
    try:
        proc = subprocess.run(cmd, cwd=cwd, text=True, capture_output=True, timeout=HARD_TIMEOUT_S)
    except subprocess.TimeoutExpired:
        return {"status": "TIMEOUT", "response": f"agy killed after {HARD_TIMEOUT_S}s", "exit_code": -1}
    try:
        out = json.loads(proc.stdout)
    except json.JSONDecodeError:
        out = {"status": "ERROR", "response": (proc.stdout + proc.stderr)[-2000:]}
    out["exit_code"] = proc.returncode
    return out


def matches(data, schema: dict) -> bool:
    """Minimal check of our own two schemas: required keys present with the right JSON types."""
    kinds = {"object": dict, "array": list, "string": str}
    if not isinstance(data, kinds[schema["type"]]):
        return False
    if schema["type"] == "object":
        return all(k in data and matches(data[k], schema["properties"][k]) for k in schema.get("required", []))
    if schema["type"] == "array":
        return all(matches(x, schema["items"]) for x in data)
    return "enum" not in schema or data in schema["enum"]


def agy_structured(prompt: str, model: str, mode: str, cwd: Path, schema: dict, log: Log, step: str):
    """agy with --json-schema; falls back to parsing `response`, retries once on empty output."""
    for attempt in (1, 2):
        res = agy(prompt, model, mode, cwd, schema)
        data = res.get("structured_output")
        if not isinstance(data, dict):
            try:
                data = json.loads(res.get("response") or "")
            except json.JSONDecodeError:
                data = None
        if res.get("status") == "SUCCESS" and matches(data, schema):
            return data
        raw = {k: v for k, v in res.items() if k != "json_schema"}
        if res.get("denied_actions"):
            prompt += ("\n\nIMPORTANT: your previous attempt tried a shell command, which is not available here. "
                       "Use only file read/list tools, then answer.")
        log.write(f"[{step}] attempt {attempt} gave no structured output: {json.dumps(raw)[:1500]}")
    return None


def changed_files(cwd: Path) -> list[str]:
    """Paths changed vs HEAD, including untracked files and deletions."""
    out = git("status", "--porcelain", "-z", "-uall", "--no-renames", cwd=cwd)
    return sorted(entry[3:] for entry in out.split("\0") if entry)


def normalise(path: str, repo: Path) -> str | None:
    """Repo-relative file path, or None if it is absolute, escapes the repo, is a directory or forbidden."""
    s = path.strip().replace("\\", "/")
    s = s[2:] if s.startswith("./") else s
    p = PurePosixPath(s)
    if not p.parts or p.is_absolute() or ".." in p.parts or s.endswith("/"):
        return None
    if p.parts[0] in FORBIDDEN_TOP or str(p).startswith(FORBIDDEN_PREFIXES):
        return None
    full = repo / str(p)
    if full.is_dir() or not full.resolve().is_relative_to(repo.resolve()):
        return None
    return str(p)


def validate_plan(plan: dict, repo: Path) -> tuple[list[dict], list[str]]:
    errors, seen_ids, owner = [], set(), {}
    subtasks = plan.get("subtasks") or []
    if not subtasks:
        errors.append("plan has no subtasks")
    for st in subtasks:
        sid = st["id"].strip()  # types already checked by matches()
        if not sid or sid in seen_ids or not sid.replace("-", "").replace("_", "").isalnum():
            errors.append(f"bad or duplicate subtask id {sid!r}")
        seen_ids.add(sid)
        files = []
        for f in st.get("files", []):
            n = normalise(f, repo)
            if n is None:
                errors.append(f"{sid}: path not allowed: {f!r}")
            elif n in owner:
                errors.append(f"{n} owned by both {owner[n]} and {sid}")
            else:
                owner[n] = sid
                files.append(n)
        if not files:
            errors.append(f"{sid}: owns no files")
        st["id"], st["files"] = sid, files
    return subtasks, errors


def run_worker(st: dict, spec: str, repo: Path, tmp: Path, model: str, log: Log) -> dict:
    wt = tmp / f"wt-{st['id']}"
    with _git_lock:  # concurrent `worktree add` can race on .git/worktrees
        git("worktree", "add", "--detach", str(wt), "HEAD", cwd=repo)
    try:
        prompt = (
            f"You are one worker in a team. Your subtask ({st['id']}):\n{st['spec']}\n\n"
            f"You may create or edit ONLY these files:\n" + "\n".join(f"- {f}" for f in st["files"]) +
            "\nRead any other file for context, but never modify it. Other workers own other files.\n\n"
            f"Overall task, for context only:\n{spec}\n\n{RULES}\n"
            "When finished, print a short list of the files you changed.")
        res = agy(prompt, model, "accept-edits", wt)
        changed = changed_files(wt)
        owned = set(st["files"])
        kept = [f for f in changed if f in owned]
        violations = [f for f in changed if f not in owned]
        ok = res.get("status") == "SUCCESS" and res["exit_code"] == 0
        for f in (kept if ok else []):  # copy (or delete) owned files into the main tree; failed runs drop all
            src, dst = wt / f, repo / f
            if src.exists():
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(src, dst)
            elif dst.exists():
                dst.unlink()
        outcome = {"id": st["id"], "status": res.get("status"), "exit": res["exit_code"],
                   "kept": kept if ok else [], "dropped_failed": [] if ok else kept, "violations": violations,
                   "missing": sorted(owned - set(changed)),
                   "seconds": round(res.get("duration_seconds") or 0),
                   "response": (res.get("response") or "").strip()[-600:]}
        log.write(f"[worker {st['id']}] status={outcome['status']} exit={outcome['exit']} "
                  f"{outcome['seconds']}s kept={outcome['kept']} dropped(failed run)={outcome['dropped_failed']} "
                  f"violations(discarded)={violations} "
                  f"untouched={outcome['missing']}")
        return outcome
    finally:
        try:
            with _git_lock:
                git("worktree", "remove", "--force", str(wt), cwd=repo)
        except subprocess.CalledProcessError as e:  # rmtree + prune in main() clean up
            log.write(f"[worker {st['id']}] worktree remove failed (will prune): {e.stderr.strip()}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("task_file")
    ap.add_argument("--workers", type=int, default=MAX_WORKERS, help=f"parallel workers (capped at {MAX_WORKERS})")
    ap.add_argument("--lead", default=LEAD)
    ap.add_argument("--worker-model", default=WORKER)
    ap.add_argument("--ref-dir", action="append", default=[],
                    help="read-only reference directory outside git (repeatable); workers can read it, never edit it")
    args = ap.parse_args()
    REF_DIRS.extend(str(Path(d).resolve()) for d in args.ref_dir)

    repo = Path(git("rev-parse", "--show-toplevel", cwd=Path.cwd()).strip())
    task_file = Path(args.task_file).resolve()
    spec = task_file.read_text(encoding="utf-8")
    if REF_DIRS:
        spec += "\n\nRead-only reference directories (absolute paths; read, never edit):\n" + \
                "\n".join(f"- {d}" for d in REF_DIRS)
    log = Log(repo / ".gemini-tasks" / f"{task_file.stem}.log")

    if changed_files(repo):
        print("Refusing to run: working tree is dirty. Commit or stash first.", file=sys.stderr)
        return 1

    log.write(f"\n=== {dt.datetime.now().isoformat(timespec='seconds')} two-tier run of {task_file.name} "
              f"(lead {args.lead}, workers {args.worker_model} x{args.workers}) at {git('rev-parse', '--short', 'HEAD', cwd=repo).strip()}")

    # 1. Plan
    plan_prompt = (
        f"You are the lead of a small team of coding agents. Read the task below and the repository as needed, "
        f"then split the task into independent subtasks for parallel workers.\n"
        f"- Every subtask lists the exact repo-relative files it will create or edit. No file may appear in "
        f"two subtasks. Group files that must agree with each other (e.g. a registry class and the code using "
        f"it) into one subtask.\n- Each subtask gets a short, self-contained spec with the exact ids, keys, "
        f"values and conventions the worker needs; workers cannot see each other.\n"
        f"- Use ids like 'lang-en', 'models-blocks' (letters, digits, - or _). At most 12 subtasks.\n"
        f"- Do not edit anything now. If the task already names every file, plan from the task text; read files "
        f"only when you need to, with your file-reading/listing tools.\n{RULES}\n\nTASK:\n{spec}")
    plan = agy_structured(plan_prompt, args.lead, "plan", repo, PLAN_SCHEMA, log, "plan")
    if changed_files(repo):
        log.write(f"[plan] ABORT: plan step changed the tree: {changed_files(repo)} (revert by hand)")
        return 1
    if plan is None:
        log.write("[plan] FAILED, aborting (nothing was changed)")
        return 1
    subtasks, errors = validate_plan(plan, repo)
    for st in subtasks:
        log.write(f"[plan] {st['id']}: {st['files']}\n        {st['spec'][:300]}")
    if errors:
        log.write("[plan] REJECTED: " + "; ".join(errors))
        return 1
    owned_all = {f for st in subtasks for f in st["files"]}

    # 2. Workers, each in its own worktree
    tmp = Path(tempfile.mkdtemp(prefix="rttc-gemini-team-"))
    try:
        with cf.ThreadPoolExecutor(max_workers=max(1, min(args.workers, MAX_WORKERS))) as pool:
            futs = [pool.submit(run_worker, st, spec, repo, tmp, args.worker_model, log) for st in subtasks]
            outcomes = []
            for fut in futs:
                try:
                    outcomes.append(fut.result())
                except Exception as e:  # worktree/git failure: record and carry on
                    outcomes.append({"id": "?", "status": "CRASH", "error": str(e)})
                    log.write(f"[worker] CRASH: {e}")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
        git("worktree", "prune", cwd=repo)

    # 3. Neon review in the main tree
    summary = json.dumps([{k: o.get(k) for k in ("id", "status", "kept", "violations", "missing", "response")}
                          for o in outcomes], indent=1)
    review_prompt = (
        f"You lead a team whose workers just finished. Check the combined result in the working tree against "
        f"the ORIGINAL TASK. Read the changed files. Fix small issues yourself (typos, missing keys, invalid "
        f"JSON, inconsistencies between files), but edit ONLY these files:\n"
        + "\n".join(f"- {f}" for f in sorted(owned_all)) +
        f"\nDo not attempt large rewrites; list them as unresolved instead.\n{RULES}\n\n"
        f"ORIGINAL TASK:\n{spec}\n\nSUBTASKS:\n{json.dumps(subtasks, indent=1)}\n\nWORKER OUTCOMES:\n{summary}\n\n"
        f"Return status 'ok' only if the task is fully met; list what you fixed and anything unresolved.")
    pre_review = set(changed_files(repo))
    review = agy_structured(review_prompt, args.lead, "accept-edits", repo, REVIEW_SCHEMA, log, "review") \
        or {"status": "issues", "fixed": [], "unresolved": ["review run gave no result; check the diff by hand"]}
    # only files the review itself touched; never anything that was already changed before it ran
    stray = [f for f in changed_files(repo) if f not in owned_all and f not in pre_review]
    for f in stray:  # revert lead edits outside the owned set
        if git("ls-files", "--", f, cwd=repo).strip():
            git("checkout", "HEAD", "--", f, cwd=repo)
        else:
            (repo / f).unlink(missing_ok=True)
    log.write(f"[review] status={review['status']} fixed={review['fixed']} unresolved={review['unresolved']}"
              + (f" reverted-stray-edits={stray}" if stray else ""))

    bad = [o for o in outcomes if o.get("status") != "SUCCESS" or o.get("violations") or o.get("missing")]
    final = changed_files(repo)
    log.write(f"[result] files changed: {final}\n[result] "
              + ("OK" if review["status"] == "ok" and not bad and not stray else "NEEDS ATTENTION")
              + " (Claude: build + review next)")
    return 0 if review["status"] == "ok" and not bad and not stray else 2


if __name__ == "__main__":
    sys.exit(main())
