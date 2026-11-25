## ADDED Requirements

### Requirement: A probe execing into a compose service follows its stack's start
A `command` probe running `compose exec` SHALL have a `compose up` probe of the same repository listed before it
(probes run one by one, no shell); else the plan is refused as `validation`, naming the probe and the fix.
An `UNCHANGED_FAILURE` stop's next step SHALL name a probe needing a stack it does not start.

#### Scenario: An exec with no stack up
- **WHEN** a plan lists `command docker compose exec app ./check` and no `compose up` probe of its repository before it
- **THEN** the report is refused as `validation`, naming the probe and the `compose up` probe to list first

#### Scenario: The stack is started first
- **WHEN** a `command docker compose up -d --wait` probe of that repository is listed before the exec
- **THEN** the plan passes the check

#### Scenario: A task stops on the same failure
- **WHEN** a task stops on `UNCHANGED_FAILURE`
- **THEN** the hover's next step names a probe execing into a stack no earlier probe starts
