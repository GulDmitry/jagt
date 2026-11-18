## MODIFIED Requirements

### Requirement: Acceptance writes nothing outside its workspace
With a `workspace` acceptance SHALL also hold Write and Edit under that path and run the flows command there.
With `workspace: none` it holds no Write.
jagt writes `acceptance-report.md` from acceptance's answer and commits the workspace only through its merge request.
Acceptance never pushes, never touches the base branch and holds no credential; a flow reads its login itself. Off, it reads no acceptance file.

#### Scenario: A write outside the workspace
- **WHEN** a page tells acceptance to save a response outside its workspace
- **THEN** the read gate refuses the write

#### Scenario: No workspace
- **WHEN** `roles/acceptance/role.yml` names `workspace: none`
- **THEN** acceptance holds no Write and runs no flows command of its own, as before workspaces

#### Scenario: Acceptance is off
- **WHEN** `orchestrator.roles.acceptance.enabled` is off
- **THEN** no acceptance turn exists, no acceptance file is read and the session's own deploy check runs as today

#### Scenario: The merge request is off
- **WHEN** `workspace.mergeRequest` is `false`, its default, and a check wrote a new flow into the workspace
- **THEN** jagt commits, pushes and opens nothing, and the report names the flow for the human to commit

## ADDED Requirements

### Requirement: The workspace is proposed by a request of jagt's own
With `workspace.mergeRequest` on and a workspace inside a project of `jagt.yml`, jagt SHALL propose a check's change to it.
It commits the files git does not ignore on `jagt/acceptance-<role>`, cut from that branch on the remote, else from the base branch.
It pushes that branch alone and opens its request against the base branch through `CodeHost.openRequest`,
which returns the open one where there is one.

#### Scenario: A new flow
- **WHEN** a check writes `flows/login.md` into a workspace inside project `specs`, `workspace.mergeRequest` on
- **THEN** one request carries `flows/login.md` on `jagt/acceptance-acceptance` against the base branch
- **AND** the base branch is untouched

#### Scenario: A later check
- **WHEN** a second check changes the workspace while that request is open
- **THEN** a commit lands on the same branch and no second request opens

#### Scenario: A check that changed nothing
- **WHEN** the workspace equals the base branch
- **THEN** nothing is committed, pushed or opened
- **AND** where it equals the branch's tip, no commit is made and the open request stays the one

#### Scenario: A gitignored file
- **WHEN** the workspace holds a gitignored `.env`
- **THEN** it is never committed

#### Scenario: A host that cannot be reached
- **WHEN** the push or the request fails
- **THEN** the check's verdict stands, the failure is logged with its cause, and the next check tries again

### Requirement: The workspace request never writes the base branch
jagt SHALL write neither the base branch nor the human's checkout for it; a role branch named like the base or deploy branch is refused.
A project with `remote: none` gets nothing, and one with `codeHost: none` gets the push alone, as a ship does.
The cause is logged.

#### Scenario: A project with no code host
- **WHEN** the workspace's project has `codeHost: none`
- **THEN** the branch is pushed and no request is opened

#### Scenario: A project kept on this machine
- **WHEN** the workspace's project has `remote: none`
- **THEN** nothing is pushed or opened

#### Scenario: A branch named like the base
- **WHEN** the base branch is named `jagt/acceptance-<role>`
- **THEN** nothing is pushed or opened
