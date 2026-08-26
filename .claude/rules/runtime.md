---
paths:
  - "**/dev/jagt/orchestrator/adapter/tmux/**"
  - "**/adapter/*Terminal*.java"
  - "**/adapter/*Kitty*.java"
  - "**/adapter/macos/**"
  - "**/adapter/linux/**"
  - "**/adapter/ProcessRunner.java"
---

No GUI or keystroke automation, ever. A detached launch gets its own session, never an ignored signal.

Full rules for terminals, sessions and spawned processes: **`docs/rules/runtime.md`** — read it before changing behaviour here.
