## Why

Matching a request to a project on the exact host broke SSH aliases, split ssh and web hosts and hosts with an underscore.

## What Changes

- The path decides; the host breaks a tie only where several projects' paths match.

## Capabilities

### Modified Capabilities

- `task-start`: which project a request belongs to.
