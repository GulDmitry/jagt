---
paths:
  - "**/dev/jagt/orchestrator/flow/**"
  - "**/dev/jagt/orchestrator/capability/**"
  - "**/FlowWiring.java"
---

Nothing below `flow/` decides a status. `withStatus` lives in `flow/` and in the record, nowhere else.

Full rules for the flow machine: **[`openspec/specs/task-flow/spec.md`](../../openspec/specs/task-flow/spec.md)** —
read it before changing behaviour here.
