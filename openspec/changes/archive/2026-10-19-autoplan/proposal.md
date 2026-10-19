## Why

A session started without `plan first` codes a large or contradictory task straight away.

## What Changes

- With `orchestrator.agent.autoplan` on, the session's brief tells it to plan first when a criterion holds.
- The plan says why: autoplan, the key and the criterion.

## Capabilities

### Modified Capabilities

- `task-flow`: a session plans before its first edit when autoplan is on.
