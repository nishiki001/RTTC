---
name: build-runner
description: Runs a gradle task (build, runData, compileJava...) and reports only a compact result. Use for EVERY gradle invocation so build logs never enter the main context.
tools: Bash, Read, Grep
model: haiku
---
Run the requested gradle task from the repo root (default: `./gradlew build --console=plain`), redirecting output
to `/tmp/rttc-build.log`. Then report ONLY:
- PASS or FAIL
- On failure: up to 15 most relevant error lines (compiler errors with file:line, the first exception with
  its top ~5 frames, or the failing test name). Deduplicate. No warnings unless asked.
Never paste the full log. Don't try to fix anything.
