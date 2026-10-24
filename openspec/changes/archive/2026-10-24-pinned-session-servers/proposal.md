## Why

A session loads every MCP server of its human's configuration, so a run that must reach only a local code host and
tracker could open a real merge request.

## What Changes

- `orchestrator.claude.mcpConfig` names a server file; a session then starts and revives with jagt's server and
  those alone (`--strict-mcp-config`). Unset, nothing changes.

## Capabilities

### New Capabilities

### Modified Capabilities

- `runtime`: a session's servers can be pinned.
