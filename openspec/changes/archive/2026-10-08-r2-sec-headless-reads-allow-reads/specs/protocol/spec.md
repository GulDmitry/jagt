## MODIFIED Requirements

### Requirement: A ticket is read through the human's MCP servers
A ticket ref SHALL be read, paid, through the human's own MCP servers for title, labels and project.
It SHALL load no built-in tool and call only MCP reads (`get*`, `list*`…) unless `allowed-tools` names more.
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
