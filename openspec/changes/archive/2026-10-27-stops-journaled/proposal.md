## Why

A stop lived only in the task, which a `lift` erases, and in a log line that rotates away.
No loop could be reviewed afterwards.

## What Changes

- Every newly tripped stop appends one line to `stops.jsonl` beside `state.json`; `lift` leaves it.
- `TaskStops` hands the log line, the notification and the journal line to `StopAnnouncements`.

## Capabilities

### Modified Capabilities

- `unattended-work`: the looping-task stop requirement journals every stop.
