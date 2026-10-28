## Why

A session whose acceptance probe was itself wrong reported PLAN_PENDING from VERIFYING and was refused:
the brief promised that report, the flow rules say a plan comes before the code.

## What Changes

- The refusal from VERIFYING names the way back: report IN_PROGRESS, then PLAN_PENDING with the new probes.
- The session brief's `probes` step promises that two-step path.

## Capabilities

### Modified Capabilities

- `task-flow`: the plan-first requirement gains the scenario "A ratified probe is replaced".
