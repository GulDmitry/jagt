# acceptance-probes Specification

## Purpose

A plan's acceptance criteria, tagged, and its acceptance checks, declared as typed probes and run by jagt, never by the session.

## Requirements

### Requirement: A plan declares its probes in one form
`plan.md` MAY hold a fenced `probes` block, one probe a line: `command [repo=<project>] [exit=<n>] <argv…>`,
`ref <ref>` or `artifact <relative path>`, at most ten (`protocol/PlanProbes`). `repo=` and `exit=` each stand at most once,
before the argv, and only a `command` takes `repo=`. A command SHALL run as argv with no shell.
A probe whose `repo=` is no repository of the task SHALL be refused as `validation`.

#### Scenario: A line no probe reads
- **WHEN** a session reports PLAN_PENDING with `command ./gradlew test | tee out.log` in its block
- **THEN** the report is refused as `validation`, naming the line

#### Scenario: A repository the task lacks
- **WHEN** a session reports PLAN_PENDING with `command repo=front npm test` and the task has no repository `front`
- **THEN** the report is refused as `validation`, naming the line

#### Scenario: A plan without probes
- **WHEN** `plan.md` holds no `probes` block
- **THEN** the task behaves as before

### Requirement: A plan carries its tagged acceptance criteria
`plan.md` SHALL hold a `## Acceptance criteria` section of at least one `- ` item, each `- [here] <text>`,
`- [elsewhere: <part>] <text>` or `- [wrong: <why>] <text>`; `, own` inside the brackets marks one the session wrote itself
(`protocol/PlanCriteria`). The criteria SHALL come from the ticket, read with every field, since a tracker may keep them in a
custom field; where it has none the session writes them. A criterion of a part this task does not build is `elsewhere`.

#### Scenario: Criteria in a custom field
- **WHEN** ABC-42 keeps its acceptance criteria in a custom field the tracker's default read omits
- **THEN** the session reads the ticket with every field and the plan lists each criterion, tagged

#### Scenario: A ticket without criteria
- **WHEN** ABC-42 names no acceptance criteria
- **THEN** the plan holds criteria the session wrote, each tagged `, own`

#### Scenario: A criterion for another part
- **WHEN** a criterion of ABC-42 describes the frontend and the task builds the backend
- **THEN** the plan keeps it as `- [elsewhere: frontend] <text>`

### Requirement: A plan without valid criteria is refused
jagt SHALL refuse a PLAN_PENDING report whose `plan.md` is missing, lacks the section or holds an item without a valid tag, as `validation`.
The Master's plan read SHALL flag a criterion the ticket names that the plan dropped or mis-tagged.

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
run every probe, five minutes each.
A probe runs in the worktree of the repository it names, the primary one where it names none.
Each result SHALL be recorded with its output's last 20 lines.
A command SHALL run under `ToolGate` and the worktree's own git hooks (`AcceptanceProbing`).

#### Scenario: A probe fails
- **WHEN** a probe fails
- **THEN** the session gets that probe and its output, and the task is IN_PROGRESS

#### Scenario: A probe names a repository
- **WHEN** a task of repositories `api` and `front` carries `command repo=front npm test`
- **THEN** it runs in `front`'s worktree

#### Scenario: A probe pushes to the base branch
- **WHEN** a command probe is `git push origin main`
- **THEN** it never runs and fails with the guard's refusal

#### Scenario: A file linked from outside
- **WHEN** an `artifact` resolves outside the worktree
- **THEN** it fails

### Requirement: The Master reads probes as facts
The Master's plan read SHALL quote the probes it ratifies; its round read SHALL quote each result (`MasterPanel`).
Its plan read SHALL flag a probe reaching another repository by a path rather than `repo=`, pattern `misplaced probe`.

#### Scenario: A round carrying probes
- **WHEN** a round reaches the Master
- **THEN** each role reads the probes and the results jagt recorded

#### Scenario: A probe reaching another repository by a path
- **WHEN** a plan of a task with repositories `api` and `web` declares `command npm --prefix ../web test`
- **THEN** the Master's plan read is not ready, naming the probe

### Requirement: A tree is verified once
A verification run SHALL be keyed on the worktree's tree state and the declared probes (`Verification.key`).
The last failing run's key is kept on the turn's marks. A question or permission request MUST NOT be verified.

#### Scenario: A question is not verified
- **WHEN** a session reports a question or a permission request
- **THEN** it does not land at `VERIFYING`, and a question standing there runs no probe and relays nothing

#### Scenario: An unchanged tree is not verified twice
- **WHEN** a task is at `VERIFYING` with the tree state and probes of its last failing run
- **THEN** no probe runs, nothing is relayed, and the task stops for you naming the standing failure

### Requirement: A multi-repository plan names every repository
For a task with several repositories, `plan.md` SHALL hold a `## Repositories` section with one item per repository,
`- [changed] <key>` or `- [unaffected: <why>] <key>` (`protocol/PlanRepositories`).
A PLAN_PENDING report SHALL be refused as `validation` for a missing section, an untagged item, a key outside the task
or a repository left out. A repository the plan lacks is asked for with `needsProject`.
On approval every repository tagged unaffected SHALL leave the task (`UnaffectedRepos`).

#### Scenario: A repository left out
- **WHEN** a task of repositories `api` and `web` reports a plan listing only `- [changed] api`
- **THEN** the report is refused as `validation`, naming `web`

#### Scenario: A repository the change does not touch
- **WHEN** the plan lists `- [unaffected: only the API changes] web` and is approved
- **THEN** the plan passes, and `web` leaves the task: its checkout is removed, its branch is kept and the session is told

#### Scenario: An unaffected repository holding own commits
- **WHEN** the approved plan lists `- [unaffected: only the API changes] web` and the task's branch in `web` holds own commits
- **THEN** `web` stays on the task and an INFO line says why

#### Scenario: The primary repository marked unaffected
- **WHEN** the approved plan lists `- [unaffected: only the web changes] api` and `api` is the primary
- **THEN** `api` stays on the task with its checkout, which holds the session's

### Requirement: A task with no repository names a changed key
A task with no repository SHALL report PLAN_PENDING only with the section and at least one `- [changed] <key>`;
any key passes, configured or not, and `[unaffected]` keys are ignored.

#### Scenario: A task with no repository names none
- **WHEN** a task with no repository reports a plan whose section lists `- [unaffected: no change] api` alone
- **THEN** the report is refused as `validation`, naming `## Repositories`

#### Scenario: A task with no repository names a key jagt.yml lacks
- **WHEN** a task with no repository reports a plan listing `- [changed] billing`, not configured
- **THEN** the plan passes

### Requirement: Scripted flows are run by jagt and judged by their report
`port/AcceptanceRunner` SHALL run the named flows of the project's flows repository against one environment.
It runs them as argv with no shell and a timeout (`adapter/flows`; `orchestrator.roles.qa.flows.driver` is `none` or `playwright`).
It answers per flow `passed`, `failed` or `unprovable` from the driver's JSON report, never its HTML.
Evidence SHALL lie under `qa/` in the worktree; jagt holds no credential, the flows repository does.

#### Scenario: A flow's test fails
- **WHEN** flow `AC-1` runs and its test fails
- **THEN** its result is `failed`, with the report and the test's attachments as evidence

### Requirement: A flow that cannot be proven is never failed
A flow that could not be run or found, or whose test passed only on retry or was skipped, SHALL be `unprovable`.

#### Scenario: A test passes only on retry
- **WHEN** flow `AC-1`'s test passed on its second attempt
- **THEN** its result is `unprovable`

#### Scenario: The command cannot start
- **WHEN** the configured command is missing or the flows repository is unreachable
- **THEN** every flow is `unprovable` and none is `failed`

#### Scenario: No driver is configured
- **WHEN** `orchestrator.roles.qa.flows.driver` is unset
- **THEN** every flow is `unprovable` and nothing runs

### Requirement: Flows run only on a listed environment
A run SHALL be refused before any process starts when its base URL is no url of `orchestrator.roles.qa.environments`;
every flow is then `unprovable`.

#### Scenario: A production URL not listed
- **WHEN** flows are asked to run against `https://prod.alpha.test` and only `https://alpha.test` is listed
- **THEN** nothing runs and every flow is `unprovable`

### Requirement: The QA check runs the flows its read names
After a QA read, jagt SHALL run the flow ids the read named on the environment it named, in the deployed worktree.
With `flows.driver` set, the accept prompt names the flows repository, asking for ids (`@AC-n` tags, else titles).
Each flow is one row of the verdict and of `qa-report.md`: `passed` is met, `failed` not met, `unprovable` unprovable.
A read naming no flow runs nothing and adds no row.

#### Scenario: A flow fails
- **WHEN** the read, told the flows repository, answers `flows: [AC-1]` on `https://alpha.test` and that flow fails
- **THEN** the verdict holds a not met row `AC-1` and `qa-report.md` carries it

#### Scenario: No flow named
- **WHEN** the read names no flow
- **THEN** no run starts and the report holds the read's rows alone

### Requirement: An unknown flows driver is refused at start
`orchestrator.roles.qa.flows.driver` other than `none` or `playwright` SHALL be refused at start, naming the key.

#### Scenario: A driver jagt does not carry
- **WHEN** `flows.driver` is `selenium`
- **THEN** start is refused with a problem on `orchestrator.roles.qa.flows.driver`
