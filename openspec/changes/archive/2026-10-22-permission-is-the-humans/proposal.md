## Why

A session's edits in the deploy worktree were denied by its CLI. It asked; the Master in `act` answered, granting
nothing, and the human never heard. Later reports at `DEPLOY_CONFLICT` replaced the card's conflict line with
unrelated sentences, so the card said NEEDS YOU about something else.

## What Changes

- `outcome=permission` marks a question only the human can answer; the Master never answers it.
- At a status a human holds, a report leaves the card's line; a question alone replaces it until the next report.

## Capabilities

### New Capabilities

### Modified Capabilities

- `protocol`: a session may say only the human can unblock it.
- `master`: in act a permission stays the human's.
- `task-flow`: a held status keeps its card line.
