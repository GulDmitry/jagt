## Why

The role checks each acceptance criterion of a ticket on a deployed environment; "qa" reads as local testing in a session. It is called `acceptance`, with no alias.

## What Changes

- Role id, folder (`roles/acceptance/`), config (`orchestrator.roles.acceptance`), eval (`acceptanceEval`, `roleEval -Prole=acceptance`) and the board's pass label (`accepted`) follow.
- **BREAKING**: `orchestrator.roles.qa` and the top-level `orchestrator.qa` are no longer read; jagt refuses to start while either is written, naming `roles.acceptance`.
- State written while the role was `qa` still loads: `roles["qa"]`, the top-level `qa`, the `qa-round` step, the `QA` origin, the `QA_FAILED` trigger and the `qa-verdicts` file are read under their new names and written so.
- The capability `qa` is removed and `acceptance` added with the same requirements.

## Impact

- Specs: `acceptance` (new), `qa` (removed), `acceptance-probes`, `agent-seams`, `cases`, `deploy-check`, `startup`, `task-stops` (names only).
- Decision `0155`; `jagt.yml.dist`, `docs/features.md`, `ARCHITECTURE.md`, `roles/`.
