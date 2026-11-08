## Why

A deployed change is checked only by reading; the project's own scripted flows can prove a criterion deterministically.

## What Changes

- `port/AcceptanceRunner` runs named flows against one environment and answers per flow passed, failed or unprovable, with evidence paths.
- `orchestrator.qa.flows` selects the driver (`none` default, `playwright`) and names the flows repository, its command and the environment allow-list.

## Impact

- Spec: `acceptance-probes`.
- `port/AcceptanceRunner`, `adapter/flows/*`, `jagt.yml.dist`, `ARCHITECTURE.md`, `docs/features.md`.
