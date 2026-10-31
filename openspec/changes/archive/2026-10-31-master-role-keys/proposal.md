## Why

The Master is one role of many: its settings belong under its own id, beside the checkers', not under a key of its own.

## What Changes

- The Master is configured under `orchestrator.roles.master`: `mode`, `model`, `brief`, `mine`.
- The old `orchestrator.master` key is still read; `roles.master` wins where both are written. One deprecation line is logged.
- The start's refusals name the new keys; the `master` command and `act` say the new key.

## Capabilities

### Added Capabilities

- `startup`: the requirement that the Master is configured under its role.

### Modified Capabilities

- `master`: the keys it reads are `orchestrator.roles.master.*`.
- `startup`: the checked keys are `orchestrator.roles.master.*`.
