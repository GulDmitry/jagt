## Why

The Master's answer to a question at `DEPLOY_CONFLICT` overwrote the hand-off in `task_context.md`, so the session
lost the conflict it was resolving.

## What Changes

- At a status waiting on jagt's instruction (`SHIPPING`, `DEPLOY_CONFLICT`) an answer is written under it.
- A later answer replaces the earlier one under it, so the file stays bounded.

## Capabilities

### New Capabilities

### Modified Capabilities

- `master`: an answer keeps the instruction jagt waits on.
