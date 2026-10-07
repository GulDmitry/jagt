## Why

Claude's auto memory is keyed by the repository, so every worktree loaded it and it rotted into ticket notes and
rules contradicting the flow ([memory-layer §8](../../../docs/research/memory-layer.md#8-measured-on-our-own-tasks-2026-10-06)).

## What Changes

- A sub-agent worktree's generated settings switch auto memory off.

## Capabilities

### Modified Capabilities

- `runtime`: a task session neither reads nor writes Claude's auto memory.
