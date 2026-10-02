# 0008 — Tasks are capped at 24, with no queue

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md#the-human-in-the-loop)

## Status

Accepted 2026-09-28 (`5c037ec`); no bulk branch cleanup 2026-08-14 (`19c3335`). Recorded retroactively 2026-10-02.

## Context

- 2026-08-13, `00a6d2a`: each task is a session, a language server (1–2 GB) and a worktree, and the laptop had
  swapped once. `agent.maxConcurrentTasks` (default 3) refused a new task and both headers showed `n/cap`.
- Same day, `77af1fd`: removed. jagt runs on other people's machines, one with 100 GB of RAM, so a number picked
  here is wrong for almost all of them. The rule became: no cap, no queue, no slots indicator.
- 2026-08-14, `19c3335`: `prune [all]` swept local branches merged into `deployBranch` across every project at
  once. `4de71c6`: `prune all` slipped past the typo rule to the paid mapper, whose nearest verb was
  `done <task>`, which removes a worktree.
- 2026-09-28, `5c037ec`: tasks were the one thing left with no ceiling.

## Decision

- `TaskProvisioning.MAX_TASKS = 24`, checked in `initializeTask` before a worktree is cut, so the disk never runs
  ahead of `state.json`.
- It bounds a board nobody can read, not a busy machine.
- At the limit `do` refuses: finish one with `done` first. Nothing waits for a free place.
- No gauge: the number is learnt when it is hit.
- Branch cleanup belongs to the one task it concerns; a retired `prune` is answered by name before any model call.

## Rejected

- A configurable cap sized to the machine (`00a6d2a`): jagt cannot know the machine it runs on.
- A queue as a new pre-NEW status: it adds a state, where refusing is one `if`.
- The `n/cap` header (`00a6d2a`): a gauge that sits there.
- No ceiling at all (`77af1fd` to `5c037ec`): tasks were the one unbounded thing.
- `prune [all]`: it reaches across every project at once, and a human who wants a branch gone has git.

## Reopen when

- Someone hits 24 while every open task is still one they work on: the board reads more than the number allows.
