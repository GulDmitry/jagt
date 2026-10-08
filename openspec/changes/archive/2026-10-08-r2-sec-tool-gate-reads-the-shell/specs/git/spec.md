## MODIFIED Requirements

### Requirement: ToolGate refuses the line
`ToolGate` (`POST /api/agent/tool`) SHALL refuse that push too. It also refuses a push naming no branch, deleting the
task's, or forcing it without the lease; and `HEAD` where the line may move it. So is any line that could skip the hook.

#### Scenario: Skipping the hook
- **WHEN** a push line carries `--no-verify`, `core.hooksPath`, `alias.`, `GIT_CONFIG*`, `env -i`, `sh -c` or `eval`, however quoted
- **THEN** refused
