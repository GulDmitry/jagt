# 0066 — Only the human opens a task

[← decisions](README.md) · rule: [`master`](../../openspec/specs/master/spec.md) · supersedes in part: [0015](0015-another-branch-is-another-task.md)

## Status

Accepted 2026-10-22.

## Context

In `act` a session asked twice for a fix outside its branch. Each answer carried the same `do` line, `MasterOpen`
ran both, and the Master shipped both: two tasks and two requests for one change, no human in between.
[0015](0015-another-branch-is-another-task.md) built that on purpose, and its guard only refused the deploy branch
and the reviewer's own question; nothing held who may open a task.

## Decision

- A task opens from a deterministic source alone: the human's own command, or tracker intake; never a model's answer.
- The Master opens no task: a `do` line in its answer opens nothing, and the session is told only the human opens one.
- Work a task needs is done in it, on its own branch; only a repository it lacks goes to the human.
- `MasterOpen` is gone; `TaskOpeningDoorsTest` lets only those doors reach `TaskLauncher`.

## Rejected

- Deduplicating the Master's `do` lines: one task the human never asked for is already the defect.
- A second task for a second branch: one task holds a worktree per repository, and a task's scope is the human's.

## Reopen when

- The human asks the Master to open tasks.
