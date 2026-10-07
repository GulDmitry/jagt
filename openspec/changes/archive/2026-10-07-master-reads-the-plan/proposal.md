## Why

A plan waited for the human alone, so a worker that misread the brief wrote code against it before anyone read it.

## What Changes

- The Master reads a `PLAN_PENDING` task's `plan.md` against the ticket; findings go back to the session.
- In `act` a plan that holds starts; `mine: [plan]` keeps that step yours.

## Capabilities

### Modified Capabilities

- `master`: reads a plan before the human.
- `attention`: a plan under Master read is the agent's move.
