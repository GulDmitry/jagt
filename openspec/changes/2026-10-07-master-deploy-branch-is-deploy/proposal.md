## Why

Asked to merge a task into dev, the Master opened a task to do it, and that task's refusal another: two sessions and a
stray request into dev, where the flow has one step for it — the task's own `deploy` once its request is green.

## What Changes

- A Master `do` line naming the project's deploy branch opens nothing; the answer prompt says the merge is `deploy`.

## Capabilities

### Modified Capabilities

- `master`: a `do` line never opens work on the deploy branch.
