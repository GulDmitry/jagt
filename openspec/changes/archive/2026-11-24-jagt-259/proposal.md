## Why

The reviewer posted on a task's request after the ship, so a defect it found there should have been fixed before the request existed.

## What Changes

- A task at REVIEW_PENDING has its local diff read by the code reviewer before any ship; each finding is relayed to the session as a review round.
- The flow refuses SHIP while a relayed finding awaits the session's answer.
- A finding the pre-ship review raised is not posted again on the task's request.

## Capabilities

### New Capabilities

### Modified Capabilities

- `code-review`: the pre-ship read, the ship waiting for its answers, and what the request is not told again.
