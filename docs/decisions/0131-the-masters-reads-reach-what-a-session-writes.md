# 0131 — The Master's reads reach what a session may write

[← decisions](README.md) · spec: [`master`](../../openspec/specs/master/spec.md)

## Status

Accepted 2026-11-03 by the owner's choice.

## Context

- A plan, round or question read was fenced to the task's worktrees (`ReadScope`).
- A session's work spans directories beside them, a spec repository among them; a read judging that work could not
  open them, so every such round ended `not ready` and asked the human.

## Decision

- **A read reaches each present directory `agent.sandbox.allowWrite` names, sandbox on or off**: read-only, after the
  worktrees, added in `ChargedReviews`, the one door every Master read passes.
- No key of its own: where a session may write is what its work may change.

## Rejected

- A separate `reads` key: a second list kept in step with `allowWrite` by hand.
- An unfenced read: a diff could steer it anywhere on disk.

## Reopens it

- A directory a session writes that a read must not see.
