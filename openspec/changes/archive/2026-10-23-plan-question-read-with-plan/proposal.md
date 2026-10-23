## Why

A plan handed back with a question was answered without `plan.md` or the ticket read, so "plan revised, approve?"
got "approve" while the plan contradicted the ticket; with `mine: [plan]` neither job read the plan.

## What Changes

- A task at `PLAN_PENDING` is reviewed whatever its message, a question included (`MasterReviewJob`).
- The answer job never answers at `PLAN_PENDING`: the plan review answers it (`MasterAnswerJob`).
- A `ready` plan verdict on a hand-back with a question sends its findings to the session, then approves (`MasterVerdicts`).

## Capabilities

### New Capabilities

### Modified Capabilities

- `master`: a plan's question is answered by reading the plan.
