## MODIFIED Requirements

### Requirement: Field and consistency rules, one report
Field rules SHALL catch a value out of its enum, a missing required field, a link nobody can open. Consistency rules
catch two fields that cannot both hold: `reviewRequests` beside `reviewRequestUrl`, CI_POLLING with no request, a
request filed under a project the task does not have.

#### Scenario: CI_POLLING without a request
- **WHEN** a message reports CI_POLLING without one
- **THEN** it is refused as inconsistent

### Requirement: Every violation at once
Validation SHALL report every violation at once, never first-failure, each naming the field and what was expected.
`startup/StartupValidation` refuses a bad install the same way.

#### Scenario: Several wrong fields
- **WHEN** a required field is missing and another is out of its enum
- **THEN** one refusal names both

### Requirement: A ticket is read through the human's MCP servers
A ticket ref SHALL be read, paid, through the human's MCP servers for title, labels and project.
It SHALL load no built-in tool. `ReadGate` SHALL refuse any call but MCP reads (`get*`, `list*`…) and
`allowed-tools`, whose bare server means its reads, your allow rules included; unreachable, every call.
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
