# 0136 — A task may plan before it has a repository

[← decisions](README.md) · rule: [`task-start`](../../openspec/specs/task-start/spec.md)
· stands under [0059](0059-plan-first-is-jagts-gate-not-the-clis-plan-mode.md),
[0068](0068-a-session-asks-for-the-repository-it-lacks.md)

## Status

Accepted 2026-11-05.

## Context

- A task is a worktree on a branch in one project, so the human picks the project before any plan exists.
- Where the change belongs is often the plan's own finding; a wrong pick costs a cut worktree and a restart.
- 0068 lets a session ask for a repository only after the task has one.
- Routing a free-text start would be a model choosing a repository, which 0066 reserves to the human.

## Decision

- The form's `plan first` with no project ticked starts a task with no project and is never routed;
  typed `do <ticket>` and tracker intake keep the router.
- The planning session runs in `scratch/<slug>` beside `state.json`: no git, no push, no ship.
- The approved plan places the task: a worktree per `[changed]` key of its `## Repositories`, the first primary.
- An unconfigured key stops the approval as the human's question; no start adds a project.

## Rejected

- A model choosing the project from the notes: the human opens a task, and a wrong guess cuts the wrong tree.
- A throwaway worktree in jagt's own repository: it would put another project's plan under jagt's git.
- The Master adding the missing project: `jagt.yml` is the human's text (0120).

## Reopen when

- Scratch folders pile up undeleted: tasks abandoned before approval need their own end.
- Humans keep ticking a project to avoid the scratch path: the path costs more than the guess.
