# 0025 — The board is no way around the token

[← decisions](README.md) · rule: [`board`](../../openspec/specs/board/spec.md)

## Status

Accepted 2026-10-08. Adds to [0023](0023-the-master-is-identified-by-a-token.md): the token also admits a board write.
That admission superseded by [0026](0026-the-guards-gaps-after-round-three.md).

## Context

- 0023 kept a sub-agent's `curl` off the Master's MCP tools. The board's `/api` let it through: `LoopbackFilter`
  admits a request with no Origin and a loopback Host, so a `curl` to `/api/tasks/actions/deploy` deployed as the
  human, and one to `/api/tasks/say` typed into another task's session.
- `X-Working-Directory` names a task and proves nothing: a session sending another worktree's path to `/mcp` or
  `/api/agent/*` acts as that task.

## Decision

- A write under `/api`, the hooks' `/api/agent/` aside, carries an Origin or the Master's token
  (`BoardWriteFilter`). A browser sends Origin on every POST, and `LoopbackFilter` has already judged it.
- `ToolGate` refuses a session's shell line naming the board's port on a loopback name, `.jagt/master-token` or
  `mcp_client.js`.
- Both are guardrails. Known to pass: a same-user process forging Origin; a URL or path assembled at runtime; the
  Read tool on the token file; another worktree's path in `X-Working-Directory`.
- Past [0022](0022-the-resume-rebase-is-the-one-rewrite-and-the-guard-is-a-guardrail.md)'s guard too: a session
  editing its own `.jagt/hooks`, the Write tool being ungated; a shared repository config (`core.fsmonitor`,
  `diff.external`) set from a worktree, which then runs in the human's checkout.
- `.jagt` at the root is its owner's alone (0700).
- The worktree header stays unauthenticated: a per-task token would sit in worktree files any same-user process
  reads, adding a step, not a boundary.

## Rejected

- `Sec-Fetch-Site`: a browser omits it outside a secure context, so a board on `server.address` would lose every
  button.
- A per-task token now: every runtime's MCP config, the hooks and the e2e harness change for the same reach.

## Reopen when

- A session acts as the human, or as another task, through the board or a worktree header.
- Sessions run as another OS user or in a sandbox: then a token becomes a boundary.
