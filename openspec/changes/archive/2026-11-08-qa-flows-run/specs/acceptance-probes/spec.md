## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: The QA check runs the flows its read names
After a QA read, jagt SHALL run the flow ids the read named on the environment it named, in the deployed worktree.
Each flow is one row of the verdict and of `qa-report.md`: `passed` is met, `failed` not met, `unprovable` unprovable.
A read naming no flow runs nothing and adds no row.

#### Scenario: A flow fails
- **WHEN** the read names `AC-1` and its flow fails
- **THEN** the verdict holds a not met row `AC-1` and `qa-report.md` carries it

#### Scenario: No flow named
- **WHEN** the read names no flow
- **THEN** no run starts and the report holds the read's rows alone

### Requirement: An unknown flows driver is refused at start
`orchestrator.roles.qa.flows.driver` other than `none` or `playwright` SHALL be refused at start, naming the key.

#### Scenario: A driver jagt does not carry
- **WHEN** `flows.driver` is `selenium`
- **THEN** start is refused with a problem on `orchestrator.roles.qa.flows.driver`
