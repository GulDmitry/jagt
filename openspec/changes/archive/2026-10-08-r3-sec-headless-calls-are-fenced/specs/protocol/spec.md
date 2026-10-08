## MODIFIED Requirements

### Requirement: Arguments are read into the message
`MessageTool` SHALL be the one path from the wire to a verb: the arguments are read into the message and judged before
the tool runs. A field the message does not declare is ignored, so a CLI a version ahead is not rejected; one
misspelling a field the call left out is refused.

#### Scenario: An unknown field
- **WHEN** a call carries a field the message does not declare
- **THEN** it is ignored and the rest judged

#### Scenario: A misspelled field
- **WHEN** a call sends `task_id` and no `taskId`
- **THEN** it is refused naming `taskId`, never run on the caller's own task

### Requirement: A ticket is read through the human's MCP servers
A ticket ref SHALL be read, paid, through the human's MCP servers for title, labels and project.
It SHALL load no built-in tool; jagt SHALL refuse any call but MCP reads (`get*`, `list*`…) and what
`allowed-tools` adds, your allow rules included, and every call when unreachable (`ReadGate`).
`assistant.mcp-config`, a path or the JSON itself, loads only the declared servers; their tool names carry no plugin prefix.

#### Scenario: Key or URL
- **WHEN** the human runs `do ABC-42` or `do <url>`
- **THEN** title, labels and project are read through those servers

#### Scenario: No server reaches the tracker
- **WHEN** the human runs `do ABC-42 <project>` and no MCP server reaches the tracker
- **THEN** the read fails naming what stopped it, and the task carries no title

#### Scenario: No summary
- **WHEN** the item has no summary
- **THEN** a short title comes from the description; a link is never invented
