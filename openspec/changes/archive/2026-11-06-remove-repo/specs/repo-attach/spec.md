## ADDED Requirements

### Requirement: A running task loses a repository
`remove-repo <task> <project>` SHALL close that repository's open request on its code host, then take the repository out of
the task as one the host closed leaves: its checkout goes, its branch stays, the session is told. A repository with no
request just leaves. The status does not change. A close that fails SHALL leave the repository on the task and report the cause.
It SHALL be human-only and typed with a project, offered only where the task holds several repositories.

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

### Requirement: A removal that strands the task is refused
`remove-repo` SHALL be refused, nothing changed, for a project not on the task and for the task's primary repository.
It SHALL be refused for a repository already deployed (`revert` first).
It SHALL be refused at `NEW`, `DEPLOY_CONFLICT` and `DONE`.

#### Scenario: A removal is refused
- **WHEN** you name a project not on ABC-42, its primary repository, or one ABC-42 has deployed
- **THEN** the press is refused with the reason, and the task, its requests and its worktrees are as they were

### Requirement: A host closes a request through its driver
The code-host seam SHALL close a request as it reads a pipeline: `github` runs `gh pr close <url>`, `gitlab` runs
`glab mr close <iid> -R <repository>`, `command` runs the project's `closeCommand` with `{request}` replaced by the request's
address, `none` and `replay` close nothing and refuse. An unset `closeCommand` SHALL refuse naming the key.

#### Scenario: A GitHub request
- **WHEN** the request is `https://github.com/acme/shop/pull/7`
- **THEN** jagt runs `gh pr close https://github.com/acme/shop/pull/7`

#### Scenario: A GitLab request
- **WHEN** the request is `https://git.example.com/acme/shop/-/merge_requests/7`
- **THEN** jagt runs `glab mr close 7 -R git.example.com/acme/shop`
