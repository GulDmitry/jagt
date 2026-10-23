## ADDED Requirements

### Requirement: A session may ask for another repository
`update_agent_status` SHALL take `needsProject`, a key `list_projects` prints, with `outcome=question` alone. A key
that is not configured, or that the task already holds, SHALL be a violation naming the keys there are. jagt SHALL
record the request on the task (`TaskState.repoRequest`) until a repository is added or the session reports anything else.

#### Scenario: A session asks for a configured project
- **WHEN** a task in `sng` reports `outcome=question` with `needsProject: schemaRegistry`
- **THEN** the request is recorded on the task and the question reaches the board

#### Scenario: An unknown or held key
- **WHEN** `needsProject` names a project `jagt.yml` lacks, or one the task holds
- **THEN** it is refused naming the keys there are, and nothing is recorded

#### Scenario: Outside a question
- **WHEN** `needsProject` comes with any other outcome
- **THEN** it is refused as a violation of `outcome`
