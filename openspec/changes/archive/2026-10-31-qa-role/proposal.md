## Why

The session checks its own deploy, and nothing judges the deployed change against the ticket. Decision 0116 makes QA a
turn holder of its own; its cases are the owner's, signed off 2026-10-30. Dispute routing, the `done` signal, scripted flows and the browser follow as changes of their own.

## What Changes

- A new `qa` capability: gate, turn, loop, endings, counting.
- The checklist is QA's first write at `accept`; no note at ship, no readiness feature at plan (the Master's question covers it).
- The after-deploy check is one step, QA's when `orchestrator.qa.enabled`, else the session's as today.

## Capabilities

### New Capabilities

- `qa`: the headless acceptance check of a deployed change against its ticket.

### Modified Capabilities

- `task-flow`: `DeployCheckJob` asks the session only when QA is off.
