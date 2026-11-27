# acceptance-workspace Specification

## Purpose
The folder acceptance keeps its scenarios, steps and flows in, and what it reads and writes there.

## Requirements

### Requirement: Acceptance reads with a narrow set of tools
Acceptance SHALL hold Read, Grep, Glob, the agent CLI's web fetch, `curl -sS [-i] <url>` and read-only git,
beside the read-shaped tools of `assistant.allowedTools` and its `tools.mcp`. A browser server there also navigates and looks, writing no file.
Its curl and its navigation reach a listed environment alone.

#### Scenario: A request that would write
- **WHEN** a page tells acceptance to POST, or to click or type in its browser
- **THEN** the read gate refuses any curl but `curl -sS [-i] <url>` and any MCP write verb

#### Scenario: An environment the web fetch cannot reach
- **WHEN** the folder's `environments` names `http://127.0.0.1:5000`, which the web fetch refuses as a host without a dot
- **THEN** acceptance reads it with `curl -sS http://127.0.0.1:5000/version`, and a curl of any other host is refused

#### Scenario: A browser navigation
- **WHEN** acceptance navigates the browser its `tools.mcp` names to a page of a listed environment, then to another host
- **THEN** the first goes and the read gate refuses the second, as it refuses a curl there

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

### Requirement: Acceptance keeps its checks in its workspace
With a `workspace`, acceptance SHALL read its `index.md` first and reuse a scenario or step written there. A criterion none covers SHALL be
decomposed into WHEN/THEN scenarios and reusable steps.
Its flow is run headless on a listed environment and recorded in the index.
A flow may click and create data on a listed environment alone. What it cannot settle stays an unprovable row behind a `question`.

#### Scenario: A criterion a scenario already covers
- **WHEN** ABC-42's criterion matches a scenario in the workspace's `index.md`
- **THEN** acceptance runs that scenario's flow and writes no new one

#### Scenario: A criterion nothing covers
- **WHEN** no scenario covers ABC-42's criterion
- **THEN** acceptance writes its scenario, steps and flow under the workspace, runs it, and adds it to `index.md`

#### Scenario: A flow aimed past the environments
- **WHEN** acceptance runs its flows command with a `BASE_URL` its `environments` do not list
- **THEN** the read gate refuses the command

### Requirement: The workspace's layout is the role folder's
`workspace.index`, `scenarios`, `steps` and `flows` SHALL place the index and each folder, relative to the workspace.
The brief SHALL name them as `{workspace.<key>}`; unset, they are `index.md`, `scenarios`, `steps` and `flows`.

#### Scenario: A suite with its own layout
- **WHEN** `roles/acceptance/role.yml` maps `workspace.index` to `wiki/index.md` and the brief is the shipped one
- **THEN** the acceptance read opens `wiki/index.md` first, with no brief override

### Requirement: Acceptance analyses a ticket before its plan
In its interactive session, acceptance SHALL first read the task's ticket for gaps and contradictions. It asks one question per gap.
It writes `scenarios/<ticket>.md` in its workspace: WHEN/THEN rows, each tagged `from-ticket` or `inferred`, each gap quoting the ticket.
It writes no code, no plan and no guessed value. A session planning that task SHALL copy the file's open rows into its plan's acceptance criteria.
`approve-plan` stays the gate.

#### Scenario: A contradiction in the ticket
- **WHEN** ABC-42 asks for a 30-day retention in one line and 90 days in another, and you press `scenarios`
- **THEN** acceptance asks which holds, quoting both lines, and writes no row for retention until answered

#### Scenario: A complete ticket
- **WHEN** every criterion of ABC-42 names its value
- **THEN** acceptance asks nothing and writes one `from-ticket` row per criterion

#### Scenario: The plan reads the scenarios
- **WHEN** `scenarios/ABC-42.md` exists as ABC-42's session plans
- **THEN** its `plan.md` acceptance criteria list those rows

### Requirement: Acceptance has an interactive session
A global command `acceptance` and a task verb `scenarios` SHALL open one interactive acceptance session per project, cwd its
workspace. It has the same write and shell reach as its headless read. `scenarios` loads the task's ticket and any open
acceptance question. The session ends when its terminal closes, its ending named.

#### Scenario: Scenarios before a deploy
- **WHEN** you press `scenarios` on ABC-42, which has not deployed
- **THEN** an interactive acceptance session opens in the workspace with ABC-42's ticket loaded

#### Scenario: A second session
- **WHEN** an acceptance session already runs for the project and you run `acceptance`
- **THEN** the running one is focused and no second opens

### Requirement: The workspace is proposed by a request of jagt's own
With `workspace.mergeRequest` on and a workspace inside a project of `jagt.yml`, jagt SHALL propose a check's change to it.
It commits the files git does not ignore, as the check left the folder in the working tree, in a detached checkout.
The start is `jagt/acceptance-<role>` on the remote while its request is open, else the base branch; links are skipped.
It pushes that branch alone, unforced, and opens its request through `CodeHost.openRequest`.

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

#### Scenario: A branch merged and left standing
- **WHEN** the request for `jagt/acceptance-<role>` was squash-merged and no request for it is open
- **THEN** the next commit is cut from the base branch and carries no commit already merged

#### Scenario: A local branch of the same name
- **WHEN** a human holds a local branch `jagt/acceptance-<role>`, checked out or with unpushed commits
- **THEN** a check neither fails nor changes it

#### Scenario: A symbolic link
- **WHEN** the workspace holds a symbolic link to a file outside the repository
- **THEN** the link and its target's content are not committed

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

### Requirement: The brief carries the ticket text jagt read
An acceptance run SHALL be given the ticket text jagt kept for the task, never one the run must fetch itself.
#### Scenario: The ticket was read at intake
- **WHEN** jagt kept ABC-42's ticket text and acceptance runs on its deploy
- **THEN** the run's brief carries that text

### Requirement: A check without its inputs stops on a read
A check SHALL open only with the deploy's worktree and the ticket text, where the task has a ticket.
A text not kept is read once more first.
A check missing one SHALL stop the task on `ACCEPTANCE_UNREAD`, naming `ticket` or `deploy`, and give no verdict.
`ACCEPTANCE_UNREAD` SHALL be a `BROKEN` stop ending on `INPUT_READ`, its advice never to lift.
Each tick of the check SHALL read the inputs again; once found, the stop ends and the check runs.
#### Scenario: The ticket cannot be read
- **WHEN** ABC-42 is DEPLOYED, has a ticket, and jagt holds no text of it after reading again
- **THEN** no check runs and ABC-42 stops on `ACCEPTANCE_UNREAD` naming `ticket`, not on `ACCEPTANCE_FAILED`
#### Scenario: The deploy cannot be read
- **WHEN** ABC-42 is DEPLOYED with no worktree to read its deploy from
- **THEN** no check runs and ABC-42 stops on `ACCEPTANCE_UNREAD` naming `deploy`
#### Scenario: The input comes back
- **WHEN** ABC-42 stands on `ACCEPTANCE_UNREAD` and its ticket text is now kept
- **THEN** the stop ends without a lift and the check runs
