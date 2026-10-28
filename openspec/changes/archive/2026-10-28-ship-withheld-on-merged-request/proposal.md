## Why

The Master shipped ABC-42 three times on open request !571 with nothing new, then !571 merged and it shipped a fourth time:
the loop stop tripped on a request that no longer existed.

## What Changes

- jagt reads from git whether a task's request is merged, and the flow withholds `ship` once every request is.
- A ship pressed on a merged request records no step, so no loop count includes it.

## Capabilities

### Modified Capabilities

- `task-flow`: the table's guard reads a merged request.
- `unattended-work`: the looping-task stop requirement gains a line under `Ships going round`.
- `git`: `ship` reads git for the merged fact.
