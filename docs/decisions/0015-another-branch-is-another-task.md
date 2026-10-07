# 0015 — Another branch is another task

[← decisions](README.md) · rule: [`master`](../../openspec/specs/master/spec.md)

## Status

Accepted 2026-10-07.

## Context

A task's request was split in two. The second needed a branch of its own; the push guard refused it, as it must,
and its message sent the session to the human. In `act` the loop stopped on a push only a human could make.

## Decision

- Work needing a branch other than the task's is a task of its own; the session asks for it, the guard says so.
- In `act` the Master opens it by a `do` line in its answer, which jagt runs as the human's own launch
  (`MasterShip.open`): the model judges, the launch gate decides.
- The guard is unchanged: a session still pushes its own branch and nothing else.

## Rejected

- Letting the guard pass a second branch: one task, one branch is what the guard and the board both rest on.
- Creating the branch through the code host's API: the same push by another door.

## Reopen when

- Master-opened tasks are ones the human removes unworked.
