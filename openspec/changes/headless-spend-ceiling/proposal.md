## Why

A headless call with no spend ceiling can burn money unwatched.

## What Changes

- Every headless run passes the agent CLI's spend ceiling, from `orchestrator.assistant.maxCostUsd` (default 0.70).
- A run that reaches it comes back as a failure naming the ceiling, never as an empty answer.

## Capabilities

### Modified Capabilities

- `runtime`: a headless run is held to a spend ceiling.
