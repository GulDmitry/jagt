## Why

Whose move it was got re-derived in several jobs, and what jagt had already done lived in memory a restart lost or in
`task_context.md` another relay overwrote: after a restart the Master pressed deploy on a deployed task and the session
heard "is deployed" again; the Master set `IN_PROGRESS` on a task already at work (decision 0076).

## What Changes

- `task/Turn` on the task in `state.json`: holder, step, id, digest, `since`, and what each step last carried.
- `flow/Turns.next` names the holder; `Move.ownerOf` and the Master's jobs read it.
- A relay opens the session's turn, its report closes it; the same step with the same facts while open sends nothing.
- The deploy check, the Master's deploy and repository presses, its unanswered questions and pending wakes are kept on
  the turn; `session-wakes.json` is no longer read.

## Capabilities

### New Capabilities

### Modified Capabilities

- `cases`: the turn is a fact on the task.
- `task-flow`: the deploy check is keyed on the turn.
- `review`, `runtime`, `usage-limits`: name what now holds the dedup and the wake.
