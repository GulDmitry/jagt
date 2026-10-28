# 0099 — The session rebases its own branch onto the moved target

[← decisions](README.md) · rule: [`git`](../../openspec/specs/git/spec.md)

## Status

Accepted 2026-10-28. Amends [0022](0022-the-resume-rebase-is-the-one-rewrite-and-the-guard-is-a-guardrail.md): the
resume rebase is no longer the one rewrite.

## Context

- A task branch cut from `release/x` lives long while other requests merge into `release/x`. Its request then
  lists commits the target already holds.
- The session was forbidden `rebase` and `--force-with-lease` outside jagt's conflicting resume rebase, so the
  list stayed wrong until a human stepped in.

## Decision

- The one rewrite of a pushed branch is a rebase of the task's OWN branch onto its request's target, or onto a new
  base the human or the Master named, pushed with `--force-with-lease`: by jagt on resume, by its session whenever
  the target moved on.
- The `ship` step orders it: `git fetch`, rebase onto `origin/<target>` where it moved, lease push, then the request.
- Still never: `--force`, `--amend` or `reset --hard` onto a pushed commit, `merge`, `cherry-pick`, a push of
  another branch, handing the human a push.
- `ToolGate` and the pre-push hook already pass a lease push of the task's branch and refuse the rest; no guard
  changes.

## Rejected

- A button or a human step: the rebase is deterministic and the session holds the worktree.
- Permission alone in the brief: a session that is never ordered to rebase ships the stale list again.

## Reopen when

- A lease push overwrites a commit someone else pushed.
