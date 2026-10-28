## Why

A session started on a task already past NEW reported IN_PROGRESS for a question alone, then idled; the watchdog
flagged it stopped.

## What Changes

- The `start` step orders IN_PROGRESS for a task new to the session only.
- A carried task keeps the board's status while the session answers; a change begun reports IN_PROGRESS.

## Capabilities

### New Capabilities

### Modified Capabilities

- `session-brief`: a session started on a carried task keeps the board status until it begins a change.
