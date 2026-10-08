# 0026 — The guard's gaps after round three

[← decisions](README.md) · rule: [`board`](../../openspec/specs/board/spec.md)

## Status

Accepted 2026-10-08. Narrows [0025](0025-the-board-is-no-way-around-the-token.md): the token no longer admits a
board write.

## Context

- `BoardWriteFilter` admitted an `/api` write carrying the Master's token, and `OriginFilter` booked it as the
  human's. Nothing sends the token there: `mcp_client.js` and `.mcp.json` reach `/mcp`.

## Decision

- A write under `/api`, the hooks' `/api/agent/` aside, carries an Origin; the Master acts through `/mcp` alone.

## Rejected

- Booking a token-carrying write as the Master's: a second door for what `/mcp` already does.

## Reopen when

- The Master needs a board verb `/mcp` has no tool for.
