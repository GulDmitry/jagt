## MODIFIED Requirements

### Requirement: Infrastructure failures are waited out
A pipeline the host says failed with every failed job on infrastructure (`runner_system_failure`, `stuck_or_timeout_failure`, `api_failure`,
`scheduler_failure`, `data_integrity_failure`; `startup_failure` of a run) SHALL be re-run by jagt after each wait of `infraWait.backoff`
(default 5m, 15m, 45m), never told to the session. The reasons SHALL be read from the host by its own CLI, not by a model. Any other reason, a
mix, or none SHALL be the code's, and the round goes to the session ([review](../review/spec.md)). A pipeline red for infrastructure keeps the
task's status. `infraWait.backoff: []` SHALL turn this off.
A stop this sets (`INFRA`, `CHECKS`) SHALL end on the first read listing the task's checks not red.
`StopReadJob` makes that read on the auto-review cadence while the stop stands; it relays, ships and presses nothing.

#### Scenario: Red on infrastructure is retried
- **WHEN** ABC-42's pipeline went red because the runner died and nothing else
- **THEN** the status stays, the session hears nothing, and the checks' hover says `<reason>: <job> · retry 1/3 at HH:MM UTC`
- **AND** at that time jagt re-reads the reasons and, when still infrastructure, re-runs only the failed jobs

#### Scenario: A retry lands green
- **WHEN** the re-run goes green
- **THEN** the round reads as any green round, and the retries are forgotten

#### Scenario: Red for the code
- **WHEN** one failed job failed its script, or the host gives a reason outside the list
- **THEN** the round goes to the session as before and the retries are forgotten

#### Scenario: Retries run out
- **WHEN** 3 retries were sent since the last lift and the pipeline is red on infrastructure again
- **THEN** the task lands `CI_FAILED` and stops as `INFRA`, the evidence naming the job, its reason and `3 retries`

#### Scenario: The failed jobs cannot be read
- **WHEN** the CLI is missing, not logged in, or the host refuses to list ABC-42's failed jobs
- **THEN** the red is the code's and the round goes to the session as before, one warning naming the cause

#### Scenario: The re-run is refused
- **WHEN** the host or the CLI refuses to re-run jobs already read as infrastructure, or cannot list them at the retry
- **THEN** the task stops as `INFRA` at once, the evidence being the CLI's or the host's own words
- **AND** the stop ends on a read, as below

#### Scenario: Infrastructure comes back
- **WHEN** a task stopped as `INFRA` or `CHECKS` and a later read lists its checks, not red
- **THEN** the stop is voided and the log says `stop voided by read` once, with the task, the trigger and the pipeline
- **AND** a deploy conflict resolved while it stood finishes on the next tick

#### Scenario: The host is still failing
- **WHEN** the read of a task stopped as `INFRA` is red, or lists no checks at all
- **THEN** the stop stands and the read is made again at the next poll

#### Scenario: A stop for judgement
- **WHEN** a task stopped as `PACE`, `STALLED`, `SHIPS`, `ROUNDS`, `MISS`, `UNCHANGED_FAILURE`, `UNDELIVERED` or `WAIT` is read green
- **THEN** the stop stands until a human lifts it

#### Scenario: A human acts while it waits
- **WHEN** ABC-42 is pushed, swept, resumed or lifted before the retry is due
- **THEN** a read that is not red on infrastructure forgets the armed retry
- **AND** a due one is dropped where the task is closed, stopped or no longer red
