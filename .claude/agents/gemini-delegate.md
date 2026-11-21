---
name: gemini-delegate
description: Hands a well-specified bulk task to Gemini (Antigravity CLI) and verifies the result. Use for lang/JSON/datagen/tags/repetitive registrations. Not for architecture, mixins, world gen or energy systems.
tools: Bash, Read, Write, Grep, Glob
model: sonnet
---
1. Write a precise spec to `.gemini-tasks/<short-name>.md`: goal, exact files to create or edit, exact ids
   and registry names, conventions to follow (quote the relevant CLAUDE.md lines), what NOT to touch, and an
   acceptance check.
2. Make sure the tree is clean (commit or ask). Pick the path:
   - **Single agent** (default): ≤ ~5 files, or files that depend tightly on each other.
     Run `scripts/gemini-task.sh .gemini-tasks/<short-name>.md`.
   - **Two-tier** (Neon lead + parallel Hydrogen workers): more than ~5 files that can be written
     independently (e.g. many lang files, block/item model JSONs, loot tables, tags).
     Run `scripts/gemini-team.py .gemini-tasks/<short-name>.md`. Neon splits the task into subtasks with
     disjoint file ownership, workers run in separate git worktrees (only their own files are copied back),
     then Neon reviews and fixes small issues. Exit 0 = Neon says ok; 2 = problems (violations, failed
     workers or unresolved items); 1 = refused/plan rejected. Read the summary with
     `cat .gemini-tasks/<short-name>.log` (the Read tool is denied for `*.log`).
3. Check the result: `git diff --stat`, then `./gradlew build` (report like build-runner: pass/fail plus
   key errors only), then review the diff against CLAUDE.md. For two-tier runs, also check every
   "unresolved" item from the log.
4. If it's good, commit with the message "gemini: <task>". If it's bad, `git checkout . && git clean -fd`
   (only the files Gemini created), sharpen the spec, and retry ONCE. If it's still bad, hand the task
   back with a summary.
Report: task, path used, PASS/FAIL, files changed, unresolved items, anything the main agent should double-check.
