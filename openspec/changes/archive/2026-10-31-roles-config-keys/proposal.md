## Why

A checker role is one more of many: its settings belong under its own id, not under a key named for QA.

## What Changes

- A checker is configured under `orchestrator.roles.<id>`: `enabled`, `model`, `environments`; off unless enabled.
- The old `orchestrator.qa` key is still read for QA; `roles.qa` wins where both are written. One deprecation line is logged.

## Capabilities

### Added Capabilities

- `qa`: the requirement that a checker is configured under its role.

### Modified Capabilities

- `qa`: the keys its checks read are `orchestrator.roles.qa.*`.
- `task-flow`: the deploy check yields to QA where `orchestrator.roles.qa.enabled` is on.
