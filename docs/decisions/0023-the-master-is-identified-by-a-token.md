# 0023 — The Master is identified by a token

[← decisions](README.md) · rule: [`protocol`](../../openspec/specs/protocol/spec.md)

## Status

Accepted 2026-10-08.

## Context

- An MCP call with no `X-Working-Directory`, or one naming a worktree no task holds, was the Master's: any local
  process, a sub-agent's `curl` included, reached `deploy_task`, `revert_task` and `initialize_task`.
- `X-Jagt-Origin: master` marked a Master session jagt no longer starts; nothing sent it.
- Root sessions are the human's own CLIs, started by hand at the root.

## Decision

- jagt draws a random token at each start into `.jagt/master-token` at the root, mode 0600, compared in constant
  time (`surface/mcp/MasterToken`).
- The root's own MCP config presents it as `X-Jagt-Master`: Claude through `headersHelper`, Codex through
  `mcp_client.js`, each reading the file only when run from the root.
- A registered worktree is a task; else the token is the Master; else 401, which makes Claude mint its header again
  after a restart.
- `X-Jagt-Origin` is gone: every `/mcp` call is origin `mcp`.

## Rejected

- A fixed token in `jagt.yml`: a manual step, and it never rotates.
- An environment variable on the launch line: jagt launches no root session.

## Reopen when

- A sub-agent reads `.jagt/master-token` to act as the Master: then the root needs an OS boundary, not a file.
- A CLI the root must serve can neither run a header helper nor spawn the bridge.
