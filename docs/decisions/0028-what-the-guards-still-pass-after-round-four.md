# 0028 — What the guards still pass after round four

[← decisions](README.md) · rule: [`git`](../../openspec/specs/git/spec.md)

## Status

Accepted 2026-10-08. Completes [0026](0026-the-guards-gaps-after-round-three.md) and
[0027](0027-a-headless-call-is-fenced-by-jagt.md): every gap they name is listed here.

## Context

- A fourth review passed the guards with `POST /api;x/…`, `127.1:8290`, `git blame --ignore-revs-file=/abs`, a bare
  `mcp__<server>`, `git send-pack` and `ABC-42:HEAD`; each is now refused.
- Each was a spelling a block-list missed. An allow-list, or a check on the form the consumer resolves, misses none.

## Decision

- `BoardWriteFilter` judges every write; only `/mcp` and `/api/agent/` spelled as routed are exempt. `ReadGate` names
  each git subcommand's options. `ToolGate` stays a block-list over one line: a guardrail.
- Known to pass, by class, each with what reopens it:
  - A line assembled at runtime: a variable, a script, `xargs`, a shell function. Reopen: a push reaches a shared
    branch that way.
  - A binary other than git, `gh`, `glab` speaking a host's API: `curl`, a language runtime. Reopen: a shared ref
    moves without `git push`.
  - A session editing its own `.jagt/hooks`, the Write tool ungated. Reopen: a hook differs from what jagt wrote.
  - A same-user process forging Origin, reading `.jagt/master-token`, or naming another worktree in
    `X-Working-Directory`. Reopen: sessions run as another user.
  - The board on port 80, reached by a URL naming no port. Reopen: its port is 80.
  - `HEAD` moved on an earlier call, then `git push origin HEAD`. Reopen: the `pre-push` hook is bypassed too.
  - A non-loopback `server.address`: any host there acts as the human. Reopen: the board leaves loopback by default.
  - Config the reviewer's git obeys: `core.fsmonitor`, `gpg.program` under `log.showSignature`,
    `blame.ignoreRevsFile`. Reopen: a read runs a program or reads a file jagt did not name.
  - A run answering in prose, without the schema's call: not proven fenced. Reopen: such an answer carries a
    tool's output.
  - An MCP read sends its arguments to its server. Reopen: a read tool writes.

## Rejected

- A shell parser in `ToolGate`: a second shell, wrong wherever the real one differs.
- `git -c` in the reviewer to unset config: the same `-c` a session's line is refused.
- Session state across lines: the `pre-push` hook already refuses what it would.

## Reopen when

- A gap's condition holds: it becomes a refusal and leaves this list.
