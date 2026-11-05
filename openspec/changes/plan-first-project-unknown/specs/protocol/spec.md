## ADDED Requirements

### Requirement: A plan with no repository names its repositories
A report of `PLAN_PENDING` from a task with no repository SHALL be refused unless `plan.md` holds `## Repositories` with at least
one `- [changed] <key>`. A key need not be configured; `[unaffected]` keys are ignored. The refusal names the section.

#### Scenario: A plan without the section
- **WHEN** a task with no repository reports `PLAN_PENDING` and `plan.md` lacks `## Repositories`
- **THEN** it is refused as a violation naming `## Repositories`

#### Scenario: Only unaffected keys
- **WHEN** the section lists `- [unaffected: no change] api` alone
- **THEN** it is refused

#### Scenario: An unconfigured key
- **WHEN** the section lists `- [changed] billing`, not in `jagt.yml`
- **THEN** the report is accepted
