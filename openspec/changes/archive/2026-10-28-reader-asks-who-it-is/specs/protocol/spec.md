## MODIFIED Requirements

### Requirement: A ticket read loads no built-in tool
A ticket read SHALL load no built-in tool. `ReadGate` SHALL refuse any call but MCP reads (`get*`, `list*`, `whoami`,
`discover*`…, `*discussions*`, `*notes*`, no `*_delete` mid-name) and `allowed-tools`. A bare server there means its reads,
your allow rules included; unreachable, every call. An answer that never reached it is discarded; a read it refused a tool
is not asked again.

#### Scenario: A read named by what it returns
- **WHEN** a round's read calls `mr_discussions`, `whoami` or `discover_tools`, then a tool the gate refuses
- **THEN** the first three run, and the read fails once, never asked again for the same refusal

#### Scenario: No server reaches the tracker, or no summary
- **WHEN** no MCP server reaches the tracker of `do ABC-42 <project>`
- **THEN** the read fails naming what stopped it, and the task carries no title
- **WHEN** the item has no summary
- **THEN** a short title comes from the description; a link is never invented
