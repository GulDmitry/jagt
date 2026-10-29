# infra-wait Specification

## Purpose

What jagt waits out instead of asking about: a pipeline the host says died on infrastructure.

## Requirements

### Requirement: Infrastructure failures are waited out
A pipeline the host says failed with every failed job on infrastructure (`runner_system_failure`, `stuck_or_timeout_failure`, `api_failure`,
`scheduler_failure`, `data_integrity_failure`; `startup_failure` of a run) SHALL be re-run by jagt after each wait of `infraWait.backoff`
(default 5m, 15m, 45m), never told to the session. The reasons SHALL be read from the host by its own CLI, not by a model. Any other reason, a
mix, or none SHALL be the code's, and the round goes to the session ([review](../review/spec.md)). A pipeline red for infrastructure keeps the
task's status. `infraWait.backoff: []` SHALL turn this off.

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

#### Scenario: The host cannot be asked
- **WHEN** the CLI is missing, not logged in, or the host refuses to list the jobs or to re-run
- **THEN** the task stops as `INFRA` at once, the evidence being the CLI's or the host's own words

#### Scenario: A human acts while it waits
- **WHEN** ABC-42 is pushed, swept, resumed or lifted before the retry is due
- **THEN** a read that is not red on infrastructure forgets the armed retry
- **AND** a due one is dropped where the task is closed, stopped or no longer red

### Requirement: A pipeline that died on infrastructure is re-run
`InfraRetryJob` (every minute) SHALL re-run a pipeline whose armed retry is due. The task MUST be open and not stopped, its checks still
red, and the host's failed jobs still all infrastructure. The armed retry and those sent SHALL live on the task, so a restart
keeps them; an overdue one fires on the first tick. A sent retry SHALL be a history step by `jagt`, counted toward the hour's ceiling.
A due retry the ceiling refuses stops the task as `PACE`.

#### Scenario: A restart mid-wait
- **WHEN** jagt restarts with a retry armed and overdue
- **THEN** the first tick re-reads the reasons and re-runs it

#### Scenario: The task closed while it waited
- **WHEN** ABC-42 is done before its retry is due
- **THEN** the job drops the entry and sends nothing
