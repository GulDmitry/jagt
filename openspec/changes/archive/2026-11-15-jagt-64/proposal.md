## Why

The board can deploy and start agents, and loopback was its only guard, so it could not be reached from another device behind an ingress.

## What Changes

- `orchestrator.auth`: `none` (default, loopback only) or `password` (one bcrypt hash), through Spring Security.
- `password` guards the board, `/api` and its streams; `/mcp` and `/api/agent/` keep their token and worktree header.
- `auth.allowedHosts` lets a Host and Origin through `LoopbackFilter`.

## Impact

- `board`: "Loopback only" becomes "Loopback, or behind a login". `attention`: one sentence of "Reaching a session".
