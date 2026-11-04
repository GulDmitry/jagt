## MODIFIED Requirements

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
