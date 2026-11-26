## ADDED Requirements

### Requirement: A plugin server needing a browser login is switched off, or marked
`claude.disabledPlugins` SHALL also be handed to every headless read that inherits the agent's own servers, as
`enabledPlugins: {"<id>": false}`; a read with pinned servers loads those alone and is handed nothing.
The agent's MCP servers SHALL be probed every ten minutes. A server not connected, needing a login included, SHALL be the
head's MCP mark, naming it, with no failed read first. A server of a plugin in `disabledPlugins` SHALL be no mark.

#### Scenario: A read inherits the servers
- **WHEN** `disabledPlugins` names `acme-tracker@market`, no MCP config is pinned and a read starts
- **THEN** the read loads no server of that plugin

#### Scenario: A server needs a login
- **WHEN** the probe finds `acme tracker` needing authentication and no read has failed
- **THEN** the head names `acme tracker` and its status

#### Scenario: Its plugin is disabled
- **WHEN** `acme-tracker@market` is in `disabledPlugins` and the probe lists `plugin:acme-tracker:tracker` needing authentication
- **THEN** the head shows no mark for it
