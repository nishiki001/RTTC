#!/usr/bin/env bash
# Usage: scripts/gemini-task.sh <task-file> [model]
# Runs the Antigravity (Gemini) CLI headless on a task spec inside this repo.
# TODO(local Claude Code): fill in GEMINI_CMD from `<cli> --help` — do not guess flags.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

TASK_FILE="${1:?task file required}"
MODEL="${2:-}"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Refusing to run: working tree is dirty. Commit or stash first." >&2
  exit 1
fi

PROMPT="$(cat "$TASK_FILE")

Rules: work only inside this repository. Follow CLAUDE.md. Do not run gradle. Do not commit.
When finished, print a short list of the files you changed."

GEMINI_CMD=( ) # e.g. ( antigravity --prompt "$PROMPT" --model "$MODEL" --yolo ) — VERIFY via --help
if [[ ${#GEMINI_CMD[@]} -eq 0 ]]; then
  echo "gemini-task.sh not configured yet (see TODO at top)." >&2
  exit 2
fi

"${GEMINI_CMD[@]}"
echo "--- diff stat ---"
git status --short
git diff --stat
