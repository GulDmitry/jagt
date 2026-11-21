## Why

The owner decided jagt is autonomous by default: the Master in `act` drives every task and the human takes over where they choose.

## What Changes

- An unset `roles.master.mode` is `act` where the human's words are readable, else `off`.
- An unset `roles.master.mine` is `[plan, deploy, revert]`; `mine` is the per-move list the human keeps.
- Both `.dist` starters describe the defaults; a decision supersedes 0018.

## Capabilities

### New Capabilities

### Modified Capabilities

- `master`: the default mode and the default `mine`.
