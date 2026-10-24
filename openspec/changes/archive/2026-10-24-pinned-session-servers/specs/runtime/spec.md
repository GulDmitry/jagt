## ADDED Requirements

### Requirement: A session's servers can be pinned
With `orchestrator.claude.mcpConfig` set, a Claude session SHALL start and revive with `--strict-mcp-config`, the
worktree's `.mcp.json` and that file as its only servers. Unset, it SHALL load the human's servers as before.

#### Scenario: A pinned stand
- **WHEN** `mcpConfig` names a local host's server file
- **THEN** the session reaches jagt and that host, and none of the human's servers
