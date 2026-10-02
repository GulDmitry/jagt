# 0004 — No git hook in a project's repository

[← decisions](README.md) · rule: [`git.md`](../rules/git.md)

## Status

Accepted 2026-08-04 (`9135f46`), refined 2026-08-28 (`3769208`), recorded retroactively 2026-10-02.

## Context

The ban arrived as one line in `CLAUDE.md` (`9135f46`): never propose, add or rely on a git hook; enforce
invariants in code and prompts. Its reason was not written down.

On 2026-08-24 (`07a7bdd`) a CLI's own tool hook began refusing a push to any branch but the task's (`ToolGate`):
the detached upstream removed only the default target, so `git push origin dev` was still the agent's to run.
That gate covered too little:

- wired for one CLI (`hooks/claude.properties`), so a Codex or Qwen session had none;
- it reads the command line, so a push built at runtime — a variable, an alias, a script — went past it;
- it needs jagt up, and an unreachable jagt refuses nothing.

## Decision

- jagt never adds a hook to a project's repository, never asks a human to install one, and no invariant depends
  on one being there. The ban is about writing into someone else's repository.
- jagt's own `pre-push` is written into the worktree jagt cut (`.jagt/hooks`, `WorktreeHooks`) and reached
  through `core.hooksPath` set by `GIT_CONFIG_*` on the launch command: that session and its children, no
  repository config written. `deploy`, `revert` and a human's shell are not gated.
- It refuses exactly one thing — a push whose destination is not the task's branch — reading the refs git
  resolved rather than a command line. `ToolGate` stays as the earlier layer where a CLI has one.
- `core.hooksPath` replaces the repository's hooks, so every client-side hook name gets a stub running the
  project's own, re-resolved at run time with the override off.
- A guardrail, not a boundary: `--no-verify` skips it as any hook.

## Rejected

- `ToolGate` alone: one CLI, a parsed string, and only while jagt is up.
- Stubs only for the hooks a repository held when its worktree was cut (`3769208`, dropped in `6e2f611`): a
  fresh clone holds samples, so a husky `pre-commit` installed later silently stopped running.
- Stubs for server-side and git-p4 hooks (dropped in `a60deb0`): they never run in a worktree.

## Reopen when

- A project's own hook stops running inside a session.
- A push to a branch other than the task's leaves the machine from a session.
