## Why

A call with no `X-Working-Directory`, or one naming a worktree no task holds, was the Master: any local process,
a sub-agent's `curl` included, could deploy, revert or open a task. `X-Jagt-Origin` was sent by nothing.

## What Changes

- jagt draws a token at each start into `.jagt/master-token` at the root; the root's `.mcp.json` (`headersHelper`)
  and `mcp_client.js` present it as `X-Jagt-Master`.
- A call that is neither a task's worktree nor carries the token is refused 401.
- `X-Jagt-Origin` is removed: every `/mcp` call is origin `mcp`.

## Capabilities

### Modified Capabilities

- `protocol`: an MCP caller is identified positively.
