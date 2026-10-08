## MODIFIED Requirements

### Requirement: A ticket is read through the human's MCP servers
A ticket ref's title, labels and project SHALL be read, paid, through the human's MCP servers.
It SHALL load no built-in tool. `ReadGate` SHALL refuse any call but MCP reads (`get*`, `list*`…, no `*_delete`
mid-name) and `allowed-tools`, whose bare server means its reads, your allow rules included; unreachable, every call.
An answer that never reached it is discarded.
`assistant.mcp-config`, a path or the JSON itself, loads only the declared servers; their tool names carry no plugin prefix.

#### Scenario: Key or URL
- **WHEN** the human runs `do ABC-42` or `do <url>`
- **THEN** all three are read through those servers

#### Scenario: No server reaches the tracker
- **WHEN** no MCP server reaches the tracker of `do ABC-42 <project>`
- **THEN** the read fails naming what stopped it, and the task carries no title

#### Scenario: No summary
- **WHEN** the item has no summary
- **THEN** a short title comes from the description; a link is never invented
