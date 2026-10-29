## Why

A flaky red needed a session to move on although a re-run went green: the read did not land from `CI_FAILED`.

## What Changes

- A round read lands from `CI_FAILED` as it does from `CI_POLLING`.
- Where the infra retry applies and where a read lands are two declarations.

## Capabilities

### Modified Capabilities

- `task-flow`: the held-status requirement gains a bullet.
