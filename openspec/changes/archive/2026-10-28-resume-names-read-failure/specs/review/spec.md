## MODIFIED Requirements

### Requirement: Resume takes over a request
`resume <url>` SHALL make the request's source branch the task and its target the base, leaving conflicts for the session.

#### Scenario: Other branch convention, or target deleted
- **WHEN** its branch follows another convention (`feature/x`), or its target branch was deleted since
- **THEN** taken over as-is; it works, only the next `ship` needs a target

#### Scenario: Branch already a task
- **WHEN** its branch belongs to a task
- **THEN** refused: two tasks cannot share a branch

#### Scenario: No working MCP server
- **WHEN** no MCP server for that host works
- **THEN** refused as unread, never missing, naming what stopped the read on one line

#### Scenario: No such request
- **WHEN** the host answers there is no such request
- **THEN** refused in those words: the one case "does not exist" is said
