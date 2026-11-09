## RENAMED Requirements

- FROM: `### Requirement: The QA check runs the flows its read names`
- TO: `### Requirement: The acceptance check runs the flows its read names`

## MODIFIED Requirements

### Requirement: Scripted flows are run by jagt and judged by their report
`port/AcceptanceRunner` SHALL run the named flows of the project's flows repository against one environment.
It runs them as argv with no shell and a timeout (`adapter/flows`; `orchestrator.roles.acceptance.flows.driver` is `none` or `playwright`).
It answers per flow `passed`, `failed` or `unprovable` from the driver's JSON report, never its HTML.
Evidence SHALL lie under `acceptance/` in the worktree; jagt holds no credential, the flows repository does.

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
- **WHEN** `orchestrator.roles.acceptance.flows.driver` is unset
- **THEN** every flow is `unprovable` and nothing runs

### Requirement: Flows run only on a listed environment
A run SHALL be refused before any process starts when its base URL is no url of `orchestrator.roles.acceptance.environments`;
every flow is then `unprovable`.

#### Scenario: A production URL not listed
- **WHEN** flows are asked to run against `https://prod.alpha.test` and only `https://alpha.test` is listed
- **THEN** nothing runs and every flow is `unprovable`

### Requirement: The acceptance check runs the flows its read names
After an acceptance read, jagt SHALL run the flow ids the read named on the environment it named, in the deployed worktree.
With `flows.driver` set, the accept prompt names the flows repository, asking for ids (`@AC-n` tags, else titles).
Each flow is one row of the verdict and of `acceptance-report.md`: `passed` is met, `failed` not met, `unprovable` unprovable.
A read naming no flow runs nothing and adds no row.

#### Scenario: A flow fails
- **WHEN** the read, told the flows repository, answers `flows: [AC-1]` on `https://alpha.test` and that flow fails
- **THEN** the verdict holds a not met row `AC-1` and `acceptance-report.md` carries it

#### Scenario: No flow named
- **WHEN** the read names no flow
- **THEN** no run starts and the report holds the read's rows alone

### Requirement: An unknown flows driver is refused at start
`orchestrator.roles.acceptance.flows.driver` other than `none` or `playwright` SHALL be refused at start, naming the key.

#### Scenario: A driver jagt does not carry
- **WHEN** `flows.driver` is `selenium`
- **THEN** start is refused with a problem on `orchestrator.roles.acceptance.flows.driver`
