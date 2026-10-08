## MODIFIED Requirements

### Requirement: Arguments are read into the message
`MessageTool` SHALL be the one path from the wire to a verb: the arguments are read into the message and judged before
the tool runs. A field the message does not declare is ignored, so a CLI a version ahead is not rejected; one
misspelling a field the call left out is refused.

#### Scenario: An unknown field
- **WHEN** a call carries an undeclared field
- **THEN** it is ignored and the rest judged

#### Scenario: A misspelled field
- **WHEN** a call sends `task_id` and no `taskId`
- **THEN** it is refused naming `taskId`, never run on the caller's own task

### Requirement: A hook's post is filtered, not refused
`protocol/SessionHookReport` SHALL accept what a hook posts without correction, since the hook throws the answer away:
what jagt cannot believe is dropped and logged once.

#### Scenario: A relative log path
- **WHEN** a hook posts one
- **THEN** it is dropped, the rest kept

### Requirement: A ticket is read through the human's MCP servers
A ticket ref SHALL be read, paid, through the human's MCP servers for title, labels and project.
It SHALL load no built-in tool. `ReadGate` SHALL refuse any call but MCP reads (`get*`, `list*`…) and
`allowed-tools`, whose bare server means its reads, your allow rules included; unreachable, every call. An answer that
never reached it is discarded.
`assistant.mcp-config`, a path or the JSON itself, loads only the declared servers; their tool names carry no plugin prefix.

#### Scenario: Key or URL
- **WHEN** the human runs `do ABC-42` or `do <url>`
- **THEN** title, labels and project are read through those servers

#### Scenario: No server reaches the tracker
- **WHEN** no MCP server reaches the tracker of `do ABC-42 <project>`
- **THEN** the read fails naming what stopped it, and the task carries no title

#### Scenario: No summary
- **WHEN** the item has no summary
- **THEN** a short title comes from the description; a link is never invented
