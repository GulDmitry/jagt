## Why

Any local process passed `LoopbackFilter`: a sub-agent's `curl` to `/api/tasks/actions/deploy` or `/api/tasks/say`
acted as the human, around the Master token ([0025](../../../../docs/decisions/0025-the-board-is-no-way-around-the-token.md)).

## What Changes

- A write under `/api`, hooks' `/api/agent/` aside, carries the page's Origin or the Master token, else 403.
- `ToolGate` refuses a session's line naming the board's port, `.jagt/master-token` or `mcp_client.js`.

## Capabilities

### Modified Capabilities

- `board`: a write is the page's or the Master's.
