## Why

Specs and the map named symbols the code lacks, and the board kept timers and a follow-up that no scenario described.

## What Changes

- `git`, `attention`, `shutdown`, `release-artifacts` name the symbols, the 20 s phase bound and the `releasecheck` job as the code holds them.
- `debug-reads` gains the bounds of both reads; `infra-wait` the refusal of a wait where waiting is off; `task-start` the refusal of a bad message value.
- `board` names its three timers and the silent follow-up; the Session-tab window requirement moves to `session-stream`.

## Capabilities

### Modified Capabilities

- `git`, `attention`, `shutdown`, `release-artifacts`, `infra-wait`, `task-start`, `board`
- `debug-reads`, `session-stream`: a requirement added
