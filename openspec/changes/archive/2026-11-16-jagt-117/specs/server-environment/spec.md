## ADDED Requirements

### Requirement: An MCP config file the container lacks is a mark on the board's head
A start SHALL read every file named by `claude.mcpConfig`, `assistant.mcpConfig` and `assistant.mcpConfigByKind`.
A file missing or unreadable SHALL be one mark on the board's head naming the key and the file, and jagt keeps running.
The mark SHALL end on the first read that finds the file.
An empty value, or a value holding the JSON itself, SHALL be no mark.

#### Scenario: A config file the container lacks
- **WHEN** `claude.mcpConfig` names a file the container lacks, and you start jagt
- **THEN** the board's head names the key and the file, and jagt keeps running

### Requirement: The image seeds the agent's folder on every start
When `JAGT_AGENT_SEED` and `JAGT_AGENT_HOME` are set and the seed folder exists, the entrypoint SHALL copy the seed over
`JAGT_AGENT_HOME` before jagt starts. Settings, MCP config and plugins SHALL be replaced; other files there SHALL be kept.

#### Scenario: A restart on a volume
- **WHEN** the container restarts with HOME on the volume and a seed folder baked in the image
- **THEN** the agent's settings and plugins are the image's, and its conversations and logins are unchanged
