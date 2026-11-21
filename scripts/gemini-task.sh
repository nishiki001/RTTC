#!/usr/bin/env bash
# Usage: scripts/gemini-task.sh <task-file> [model]
# Runs the Antigravity (Gemini) CLI headless on a task spec inside this repo.
# Model defaults to gemini-4-neon-high; pass e.g. gemini-4-hydrogen-medium as 2nd arg.
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

# Antigravity CLI (`agy`, verified against `agy --help` / `agy models`):
#   -p                    one-shot headless (print) mode
#   --model               e.g. gemini-4-neon-high, gemini-4-hydrogen-medium (`agy models` lists all)
#   --mode accept-edits   auto-approve file edits only (not shell commands)
#   --print-timeout       safety cap so a stuck run can't hang forever
MODEL="${MODEL:-gemini-4-neon-high}"
GEMINI_CMD=( agy -p "$PROMPT" --model "$MODEL" --mode accept-edits --print-timeout 20m )

"${GEMINI_CMD[@]}"
echo "--- diff stat ---"
git status --short
git diff --stat
