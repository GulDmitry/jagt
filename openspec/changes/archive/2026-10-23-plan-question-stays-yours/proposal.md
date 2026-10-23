## Why

In act with `mine: [plan]`, a session asked a question with its autoplan plan, the Master answered it, and the
answer moved the task off PLAN_PENDING with the plan gate still closed: no card asked for the approval, and every
write was refused.

## What Changes

- A question at a plan the Master may not approve is left to the human, as a permission is (`MasterAnswerJob`).

## Capabilities

### New Capabilities

### Modified Capabilities

- `master`: a question put with a plan that is the human's is the human's too.
