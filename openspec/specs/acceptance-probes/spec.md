# acceptance-probes Specification

## Purpose

A plan's acceptance criteria, tagged, and its acceptance checks, declared as typed probes and run by jagt, never by the session.

## Requirements

### Requirement: A plan declares its probes in one form
`plan.md` MAY hold a fenced `probes` block, one probe a line: `command [exit=<n>] <argv…>`, `ref <ref>` or
`artifact <relative path>`, at most ten (`protocol/PlanProbes`). A command SHALL run as argv with no shell.

#### Scenario: A line no probe reads
- **WHEN** a session reports PLAN_PENDING with `command ./gradlew test | tee out.log` in its block
- **THEN** the report is refused as `validation`, naming the line

#### Scenario: A plan without probes
- **WHEN** `plan.md` holds no `probes` block
- **THEN** the task behaves as before

### Requirement: A plan carries its tagged acceptance criteria
`plan.md` SHALL hold a `## Acceptance criteria` section of at least one `- ` item, each `- [here] <text>`,
`- [elsewhere: <part>] <text>` or `- [wrong: <why>] <text>`; `, own` inside the brackets marks one the session wrote itself
(`protocol/PlanCriteria`). The criteria SHALL come from the ticket, read with every field, since a tracker may keep them in a
custom field; where it has none the session writes them. A criterion of a part this task does not build is `elsewhere`.
jagt SHALL refuse a PLAN_PENDING report whose `plan.md` is missing, lacks the section or holds an item without a valid tag, as `validation`.
The Master's plan read SHALL flag a criterion the ticket names that the plan dropped or mis-tagged.

#### Scenario: Criteria in a custom field
- **WHEN** ABC-42 keeps its acceptance criteria in a custom field the tracker's default read omits
- **THEN** the session reads the ticket with every field and the plan lists each criterion, tagged

#### Scenario: A ticket without criteria
- **WHEN** ABC-42 names no acceptance criteria
- **THEN** the plan holds criteria the session wrote, each tagged `, own`

#### Scenario: A criterion for another part
- **WHEN** a criterion of ABC-42 describes the frontend and the task builds the backend
- **THEN** the plan keeps it as `- [elsewhere: frontend] <text>`

#### Scenario: A plan without the section
- **WHEN** a session reports PLAN_PENDING and `plan.md` has no `## Acceptance criteria` section, or no `plan.md` exists
- **THEN** the report is refused as `validation`, the task stays IN_PROGRESS

#### Scenario: An item without a tag
- **WHEN** an item reads `- the list shows the status`
- **THEN** the report is refused as `validation`, naming the line

### Requirement: The probes run are the probes ratified
jagt SHALL read the block once, as the task enters PLAN_PENDING, into `state.json` (`AgentStatusReports`). An edit to
`plan.md` after that SHALL change nothing until the plan is put up again.

#### Scenario: The session edits a probe after the go
- **WHEN** `plan.md` changes while the task is IN_PROGRESS
- **THEN** VERIFYING runs the probes read when the plan was put up

### Requirement: jagt runs the probes at VERIFYING
A hand-back of a task carrying probes SHALL wait at VERIFYING. Once the project's command passes, `VerifyJob` SHALL
run every probe in the primary worktree, five minutes each. Each result SHALL be recorded with its output's last 20 lines.
A command SHALL run under `ToolGate` and the worktree's own git hooks (`AcceptanceProbing`).

#### Scenario: A probe fails
- **WHEN** a probe fails
- **THEN** the session gets that probe and its output, and the task is IN_PROGRESS

#### Scenario: A probe pushes to the base branch
- **WHEN** a command probe is `git push origin main`
- **THEN** it never runs and fails with the guard's refusal

#### Scenario: A file linked from outside
- **WHEN** an `artifact` resolves outside the worktree
- **THEN** it fails

### Requirement: The Master reads probes as facts
The Master's plan read SHALL quote the probes it ratifies; its round read SHALL quote each result (`MasterPanel`).

#### Scenario: A round carrying probes
- **WHEN** a round reaches the Master
- **THEN** each role reads the probes and the results jagt recorded

### Requirement: A tree is verified once
A verification run SHALL be keyed on the worktree's tree state and the declared probes (`Verification.key`).
The last failing run's key is kept on the turn's marks. A question or permission request MUST NOT be verified.

#### Scenario: A question is not verified
- **WHEN** a session reports a question or a permission request
- **THEN** it does not land at `VERIFYING`, and a question standing there runs no probe and relays nothing

#### Scenario: An unchanged tree is not verified twice
- **WHEN** a task is at `VERIFYING` with the tree state and probes of its last failing run
- **THEN** no probe runs, nothing is relayed, and the task stops for you naming the standing failure
