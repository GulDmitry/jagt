## Why

Every caller but the watchdog stamped the generation of the task its read landed on, so a late read was never dropped.

## What Changes

- Each caller that reads and then moves passes the generation its read began at: the round and merge reads, the head a session's tips were read at, and the deploy pipeline reads.
- The deploy commit joins the generations a read carries.

## Capabilities

### Modified Capabilities

- `task-flow`: a read lands only on the generation it was taken at.
