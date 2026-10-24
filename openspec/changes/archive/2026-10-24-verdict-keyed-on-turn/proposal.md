## Why

A Master's verdict answered a hand-back by the task's `statusSince`, a clock reading, while the typed turn already
counts every hand-over (decision 0077's reopen condition).

## What Changes

- A report starts a turn with an id of its own, as a relay does.
- A verdict stores the turn id it was asked about and answers that turn alone; a restamp moves it to the turn in front.

## Capabilities

### New Capabilities

### Modified Capabilities

- `cases`: the turn's id keys the Master's verdict.
