## REMOVED Requirements

### Requirement: A multi-repository plan names every repository
**Reason**: Split into shorter requirements, every fact kept.
**Migration**: None.

### Requirement: The acceptance check runs the flows its read names
**Reason**: Split into shorter requirements, every fact kept.
**Migration**: None.

## ADDED Requirements

### Requirement: A multi-repository plan lists every repository
For a task with several repositories, `plan.md` SHALL hold a `## Repositories` section with one item per repository,
`- [changed] <key>` or `- [unaffected: <why>] <key>` (`protocol/plan/PlanRepositories`).
A PLAN_PENDING report SHALL be refused as `validation` for a missing section, an untagged item, a key outside the task
or a repository left out. A repository the plan lacks is asked for with `needsProject`.

#### Scenario: A repository left out
- **WHEN** a task of repositories `api` and `web` reports a plan listing only `- [changed] api`
- **THEN** the report is refused as `validation`, naming `web`

### Requirement: An acceptance read's flows are run
After an acceptance read, jagt SHALL run the flows the read named, each with its criterion, on the environment it named, in the deployed worktree.
With `flows.driver` set, the accept prompt names the flows repository, asking each flow's id (`@AC-n` tag, else title) and criterion.
A read naming no flow runs nothing and adds no row.

#### Scenario: No flow named
- **WHEN** the read names no flow
- **THEN** no run starts and the report holds the read's rows alone

### Requirement: An approved plan drops its unaffected repositories
On approval every repository tagged unaffected SHALL leave the task (`UnaffectedRepos`).

#### Scenario: A repository the change does not touch
- **WHEN** the plan lists `- [unaffected: only the API changes] web` and is approved
- **THEN** the plan passes, and `web` leaves the task: its checkout is removed, its branch is kept and the session is told

#### Scenario: An unaffected repository holding own commits
- **WHEN** the approved plan lists `- [unaffected: only the API changes] web` and the task's branch in `web` holds own commits
- **THEN** `web` stays on the task and an INFO line says why

#### Scenario: The primary repository marked unaffected
- **WHEN** the approved plan lists `- [unaffected: only the web changes] api` and `api` is the primary
- **THEN** `api` stays on the task with its checkout, which holds the session's

### Requirement: A flow's result is a row of the verdict
A flow naming no criterion SHALL be one row of the verdict and of `acceptance-report.md`: `passed` is met, `failed` not met, `unprovable` unprovable.
A flow naming one decides that criterion's row, as [`acceptance`](../acceptance/spec.md) says.

#### Scenario: A flow fails
- **WHEN** the read, told the flows repository, answers `flows: [{id: AC-1, criterion: ""}]` on `https://alpha.test` and that flow fails
- **THEN** the verdict holds a not met row `AC-1` and `acceptance-report.md` carries it
