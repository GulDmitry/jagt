# 0029 — What the guards still pass after round five

[← decisions](README.md) · rule: [`git`](../../openspec/specs/git/spec.md)

## Status

Accepted 2026-10-08. Supersedes the claim of [0028](0028-what-the-guards-still-pass-after-round-four.md) that
its list is complete; its rows stand, and those below join them.

## Context

- A fifth review passed the guards with `{/abs/secret,README}` in a git read, a Glob's `.{.,}/`, `git -C` on a
  bare repository the diff committed, `gh pr merge --admin`, `glab mr merge`, `git push --dele` and `-ud`, and
  `git -c remote.origin.push=…`; each is now refused.
- Each was again a spelling a block-list missed.

## Decision

- A line jagt must judge is allowed only where jagt reads all of it. A reviewer's git line is plain words:
  `[A-Za-z0-9._/=:@%+,-]` and single spaces, no word opening with `=`, no `%G`; `-C` names a worktree exactly. A
  Glob pattern holds no brace and no `..`. `gh` and `glab` run only their reads.
- A push's own spellings stay a block-list in `ToolGate`: a guardrail, the `pre-push` hook behind it.
- Known to pass, beyond 0028's rows, each with what reopens it:
  - An alias already in a git config
    ([0022](0022-the-resume-rebase-is-the-one-rewrite-and-the-guard-is-a-guardrail.md)). Reopen: a push reaches a
    shared branch that way.
  - A shared repository config set from a worktree, run in the human's checkout
    ([0025](0025-the-board-is-no-way-around-the-token.md)). Reopen: the human's git runs a program jagt saw set.
  - An MCP read verb of a local server reading outside the worktrees: a filesystem `read_file`, a `notepad_read`.
    Reopen: a read tool the human names takes a path.
  - The board's port spelled without a colon (`nc 127.0.0.1 8290`, `/dev/tcp/…`), still needing a forged Host
    and Origin. Reopen: a write lands that way.
  - A deny glob mid-name (`mcp__*__*_delete`): the CLI's acceptance of it is unproven. Reopen: a run shows the
    CLI ignores it.

## Rejected

- Parsing every shell spelling of a read: a second shell, wrong wherever the real one differs.
- `gh`/`glab` writes refused verb by verb: a new verb passes until named.

## Reopen when

- A gap's condition holds: it becomes a refusal and leaves this list.
