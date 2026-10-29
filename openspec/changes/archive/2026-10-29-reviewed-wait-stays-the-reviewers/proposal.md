## Why

A `REVIEWED` task waiting on a human approval turned `YOU`, amber, once the 24h poll window elapsed: nothing was wanted of the human, the reviewer moves it.

## What Changes

- A request only a reviewer moves on (`REVIEWED`, `APPROVED`, `DEPLOYED`) is polled past the window, its interval lengthening from `maxIntervalMinutes` to `autoReview.waitingMaxIntervalMinutes` (default 240) over a second window.
- Its window never elapses, so the card stays CI's with no attention asked.
- Other rounds keep `polling stopped` at the window.

## Capabilities

### Modified Capabilities

- `unattended-work`: the poll window and the interval of a wait on the reviewers.
