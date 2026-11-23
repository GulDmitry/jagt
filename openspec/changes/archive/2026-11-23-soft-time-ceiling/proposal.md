## Why

A suite past its ceiling on a loaded machine was killed unfinished, so the gate learned nothing; a ceiling exists to
measure, not to discard a run.

## What Changes

- A run past its ceiling finishes and its result counts; one WARN line names the suite, its minutes, the load and the power state.
- Only a hang guard, three times the ceiling, stops a run, named as hung.
- `suite-stats.jsonl` marks each run fresh or not; the slower-than-trend warning reads fresh runs only.

## Capabilities

### New Capabilities

### Modified Capabilities

- `engineering`: a ceiling is soft, a hang guard stops.
