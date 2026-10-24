## Why

A nudge sat unsent in a session's input for 17 minutes: the relay's three Enter presses over two seconds landed while
the session was still finishing its turn and ignoring keys.

## What Changes

- `NudgeResubmitJob` reads, every few seconds, the window of a task whose turn the session holds; jagt's own nudge
  alone in the input of a session showing no running turn gets Enter again, five times at most per turn, counted on
  the turn's marks.
- A human's draft is never touched.

## Capabilities

### New Capabilities

### Modified Capabilities

- `master`: a nudge left unsent is submitted again whenever its session is idle, not only within the relay's seconds.
