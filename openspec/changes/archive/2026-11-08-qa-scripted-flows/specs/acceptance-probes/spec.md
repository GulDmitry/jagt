## ADDED Requirements

### Requirement: Scripted flows are run by jagt and judged by their report
`port/AcceptanceRunner` SHALL run the named flows of the project's flows repository against one environment.
It runs them as argv with no shell and a timeout (`adapter/flows`; `orchestrator.qa.flows.driver` is `none` or `playwright`).
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
- **WHEN** `orchestrator.qa.flows.driver` is unset
- **THEN** every flow is `unprovable` and nothing runs

### Requirement: Flows run only on a listed environment
A run SHALL be refused before any process starts when its base URL is not in `orchestrator.qa.flows.environments`;
every flow is then `unprovable`.

#### Scenario: A production URL not listed
- **WHEN** flows are asked to run against `https://prod.alpha.test` and only `https://alpha.test` is listed
- **THEN** nothing runs and every flow is `unprovable`
