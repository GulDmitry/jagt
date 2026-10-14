# acceptance-probes Specification

## Purpose

A plan's acceptance checks, declared as typed probes and run by jagt, never by the session.

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
