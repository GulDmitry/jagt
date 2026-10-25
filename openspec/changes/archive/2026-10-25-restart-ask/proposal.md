## Why

A report sent while jagt was down is lost, and the session, its turn still open, waits on a reply nobody will give.

## What Changes

- After each start, a running session holding the open turn is asked once to repeat its last report (step `restarted`).
- A task with no open session turn, or whose session is not running, is asked nothing; no session is revived for it.

## Capabilities

### New Capabilities

### Modified Capabilities

- `session-brief`: a start asks each running session holding an open turn to repeat its last report.
