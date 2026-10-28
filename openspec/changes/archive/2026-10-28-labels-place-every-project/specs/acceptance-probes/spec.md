## ADDED Requirements

### Requirement: A multi-repository plan names every repository
For a task with several repositories, `plan.md` SHALL hold a `## Repositories` section with one item per repository,
`- [changed] <key>` or `- [unaffected: <why>] <key>` (`protocol/PlanRepositories`).
A PLAN_PENDING report SHALL be refused as `validation` for a missing section, an untagged item, a key outside the task
or a repository left out. A repository the plan lacks is asked for with `needsProject`.

#### Scenario: A repository left out
- **WHEN** a task of repositories `api` and `web` reports a plan listing only `- [changed] api`
- **THEN** the report is refused as `validation`, naming `web`

#### Scenario: A repository the change does not touch
- **WHEN** the plan lists `- [unaffected: only the API changes] web`
- **THEN** the plan passes and the task keeps both repositories
