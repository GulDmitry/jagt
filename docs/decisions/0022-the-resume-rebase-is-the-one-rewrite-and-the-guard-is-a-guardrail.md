# 0022 — The resume rebase is the one rewrite, and the guard is a guardrail

[← decisions](README.md) · rule: [`git`](../../openspec/specs/git/spec.md)

## Status

Accepted 2026-10-08. Supersedes the 0005 bullet "no `--force`, no `--force-with-lease`" and widens 0004's
"`--no-verify` skips it".

## Context

- Resume rebases a task branch onto its request's target and pushes it back under `--force-with-lease`
  (`GitWorktrees.rebaseOntoTarget`); after a conflict the session finishes the rebase and pushes the same way
  (`TaskResume`). 0005 forbade both.
- 0004 names `--no-verify` as the way past the pre-push hook. A review found more: `-c core.hooksPath`, an inline
  alias, `GIT_CONFIG_*` overrides, `env -i`, `sh -c`, and `HEAD` pushed after `git switch main`. `ToolGate` passed
  every one.
- A task named `main`, or resumed from a `dev` → `main` request, turned that lease push onto a shared branch.

## Decision

- Nothing rewrites what left the machine, with ONE exception: the resume rebase pushes the task's own branch under
  `--force-with-lease`, by jagt or by its session. Never another branch, never `--force`.
- A task named after any base or deploy branch is refused at creation, so the exception never reaches a shared one.
- `ToolGate` refuses a push line that could skip the hook (`--no-verify`, `core.hooksPath`, `alias.`,
  `GIT_CONFIG*`, `env -i`/`-u`, `sh -c`, `eval`), and `HEAD` on a line that may move it.
- Both stay a guardrail, not a boundary. Known to pass both: a push assembled at runtime, an alias already in a git
  config, a script that skips the hook itself.

## Rejected

- Rebasing without pushing: the request would keep the old commits, and the next ship would need the lease anyway.
- Refusing every `HEAD` push: a session pushes `HEAD` from its own worktree, which is the task's branch.

## Reopen when

- A push off the task's branch leaves a session's machine.
- The resume rebase overwrites a commit someone else pushed.
