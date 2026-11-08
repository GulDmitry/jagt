## MODIFIED Requirements

### Requirement: A running task loses a repository
`remove-repo <task> <project>` SHALL close that repository's open request on its code host, then take the repository out of
the task as one the host closed leaves: its checkout goes, its branch stays, the session is told. A repository with no
request just leaves. The status does not change. A close that fails SHALL leave the repository on the task and report the cause.
It SHALL be typed with a project, offered only where the task holds several repositories.

#### Scenario: A repository with a request is removed
- **WHEN** you type `remove-repo ABC-42 schemaRegistry` on ABC-42, which works in `api` and `schemaRegistry`
- **THEN** the request of `schemaRegistry` is closed on its host, the repository leaves ABC-42 and the session is told
- **AND** ABC-42 keeps its status

#### Scenario: A repository with no request is removed
- **WHEN** `schemaRegistry` has no request on ABC-42
- **THEN** it leaves with no call to a host

#### Scenario: The host refuses the close
- **WHEN** the host refuses to close the request
- **THEN** `schemaRegistry` stays on ABC-42 and the refusal carries the host's own words

#### Scenario: Nothing to remove
- **WHEN** ABC-42 works in one repository
- **THEN** `remove-repo` is not offered
