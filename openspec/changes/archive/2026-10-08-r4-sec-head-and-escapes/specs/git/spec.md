## MODIFIED Requirements

### Requirement: ToolGate refuses the line
`ToolGate` (`POST /api/agent/tool`) SHALL refuse that push too. It also refuses a push naming no branch, deleting the
task's, or forcing it without the lease; `HEAD` unless bare and unmoved; `send-pack`, `http-push`, `receive-pack`
and a non-GET `gh`/`glab api`; any line naming the board's port or the Master's token.

#### Scenario: Skipping the hook
- **WHEN** a line could skip the hook: `--no-verify`, `core.hooksPath`, `alias.`, `GIT_CONFIG*`, `env -i`, `sh -c`, `eval`, however quoted or escaped
- **THEN** refused
