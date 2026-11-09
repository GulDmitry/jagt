## ADDED Requirements

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
jagt writes `acceptance-report.md` from acceptance's answer and never commits the workspace.
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
