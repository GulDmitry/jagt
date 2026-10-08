# 0026 — The guard's gaps after round three

[← decisions](README.md) · rule: [`board`](../../openspec/specs/board/spec.md)

## Status

Accepted 2026-10-08. Narrows [0025](0025-the-board-is-no-way-around-the-token.md): the token no longer admits a
board write.

## Context

- `BoardWriteFilter` admitted an `/api` write carrying the Master's token, and `OriginFilter` booked it as the
  human's. Nothing sends the token there: `mcp_client.js` and `.mcp.json` reach `/mcp`.
- A third review passed `ToolGate` with `2>&1 main`, `g'i't push`, `localhost:08290` and a quoted token name; each
  is now refused. What it found that the guard still passes is listed below, so nobody takes it for covered.

## Decision

- A write under `/api`, the hooks' `/api/agent/` aside, carries an Origin; the Master acts through `/mcp` alone.
- `ToolGate` stays a guardrail. Known to pass it, beyond
  [0022](0022-the-resume-rebase-is-the-one-rewrite-and-the-guard-is-a-guardrail.md) and 0025:
  - `HEAD` moved on an EARLIER call: `git switch main`, then `git push origin HEAD` alone. `HEAD_MOVES` reads one
    line; the worktree's `pre-push` refuses the push.
  - A non-loopback `server.address`: any host on that network sends the board's own Origin and acts as the human.
    `ToolGate` refuses the address, a remote host runs no `ToolGate`.
  - A shared repository config a session sets (`diff.external`, `core.fsmonitor`) also runs in the Master's
    reviewer, whose `git diff` and `git status` [0027](0027-a-headless-call-is-fenced-by-jagt.md) allows.
  - An MCP read a headless call is given still sends its arguments to that server.

## Rejected

- Booking a token-carrying write as the Master's: a second door for what `/mcp` already does.
- Tracking `HEAD` across calls: the gate would hold session state for what the `pre-push` hook already refuses.

## Reopen when

- The Master needs a board verb `/mcp` has no tool for.
- A gap above is used: it becomes a refusal, not a line here.
