---
paths:
  - "**/dev/jagt/orchestrator/adapter/agent/**"
  - "**/adapter/assistant/**"
  - "**/dev/jagt/orchestrator/port/**"
---

Adding a vendor is one implementation plus a config value — never an `if claude` in the task flow. The
tracker and the code host are not jagt's seams: a model reads them through your own MCP.

Full rules for the pluggable seams and the headless assistant: **`docs/rules/seams.md`** — read it before changing behaviour here.
