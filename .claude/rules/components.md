---
paths:
  - "**/service/StateService.java"
  - "**/dev/jagt/orchestrator/surface/mcp/**"
  - "**/dev/jagt/orchestrator/startup/**"
  - "**/service/TaskLauncher.java"
  - "**/task/TaskState.java"
---

A task is created with its item's own facts or not at all. `state.json` never fails soft.

Full rules for state, task creation, MCP scoping and startup checks: **`docs/rules/components.md`** — read it before changing behaviour here.
