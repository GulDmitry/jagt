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
A stop this sets (`INFRA`, `CHECKS`) SHALL end on the first read listing the task's checks not red.
`StopReadJob` makes that read every 5 minutes while the stop stands, whatever auto-review says; it relays, ships and presses nothing.
A step that never landed (`UNDELIVERED`) SHALL end when `SessionAliveJob` finds the session's window running again, which it looks for every minute.

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

#### Scenario: The probe records what it read
- **WHEN** a task stopped as `INFRA` is read green after the outage
- **THEN** the task's checks and approval read that green, not the pre-outage word
- **AND** the read writes nothing else: no relay, no status move, no message to the session

#### Scenario: The request is gone
- **WHEN** a task stopped as `INFRA` or `CHECKS` and the host says a request it was counted on no longer exists or was closed
- **THEN** the stop is voided and the log says `stop voided by read` once, with `request=gone`
- **AND** a request the stop was not counted on that is gone leaves the stop standing
- **AND** a read refused because the host or CLI cannot answer leaves the stop standing

#### Scenario: The request is unlinked
- **WHEN** a task stopped as `INFRA` or `CHECKS` has no request linked any more
- **THEN** the stop is voided as above

#### Scenario: A task past its review
- **WHEN** ABC-42 is `DEPLOYED`, `DEPLOY_CONFLICT` or `REVERTED` and its open request's pipeline dies on infrastructure
- **THEN** no retry is armed or sent and no `INFRA` stop is set
- **AND** a read of that request leaves a retry armed for the deploy pipeline standing

#### Scenario: The host is still failing
- **WHEN** the read of a task stopped as `INFRA` is red, or lists no checks at all
- **THEN** the stop stands and the read is made again at the next poll

#### Scenario: The session comes back
- **WHEN** a task stopped as `UNDELIVERED` and its session's window is running again
- **THEN** the stop is voided and the log says `stop voided by read` once, with the task, the trigger and `read=session alive`
- **AND** a window still not running, or one that cannot be listed, leaves the stop standing

#### Scenario: A stop for judgement
- **WHEN** a task stopped as `PACE`, `STALLED`, `SHIPS`, `ROUNDS`, `MISS` or `UNCHANGED_FAILURE` is read green
- **THEN** the stop stands until a human lifts it

#### Scenario: A human acts while it waits
- **WHEN** ABC-42 is pushed, swept, resumed or lifted before the retry is due
- **THEN** a read that is not red on infrastructure forgets the armed retry
- **AND** a due one is dropped where the task is closed, stopped or no longer red

### Requirement: A deploy stands on the pipeline jagt reads on its commit
A deploy SHALL stand only once the host's pipeline on each repository's `deployCommit` is green.
`DeployCheckJob` reads it every minute through the host's own CLI, not a model, until the deploy is asked about
([0109](../../../docs/decisions/0109-a-deploys-outcome-is-the-pipeline-jagt-reads-on-its-commit.md)).
A pipeline running, not started, unknown or unreadable keeps the deploy open, and the task's line says `waiting on its pipeline`.
A pipeline red on infrastructure SHALL be retried and counted as a request's is, and stops the task as `INFRA` after the last retry.

#### Scenario: The deploy pipeline is not green yet
- **WHEN** ABC-42 is `DEPLOYED` and the pipeline on its deploy commit is running, not started or cannot be read
- **THEN** its session is not asked and the task stays `DEPLOYED`
- **AND** its line reads `deployed to dev — waiting on its pipeline: proj 01234567: pipeline running`

#### Scenario: The deploy pipeline goes green
- **WHEN** the pipeline on every deploy commit of ABC-42 reads green
- **THEN** its session is asked once, and the host is read no more for that deploy

#### Scenario: The deploy pipeline dies on infrastructure
- **WHEN** the pipeline on ABC-42's deploy commit went red with every failed job on infrastructure
- **THEN** the deploy stays open and its line names the retry
- **AND** after the last retry the task stops as `INFRA`

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

### Requirement: A session waits out a dependency that is down
A session whose dependency is down SHALL report IN_PROGRESS with `outcome=wait`, its message naming what is down and how to probe it.
The turn SHALL stay the session's and nothing SHALL ask the human. After each wait of `infraWait.backoff` jagt SHALL say `wait-over`.
Once the session answered `wait-over` with a wait as many times as there are waits, the task SHALL stop as `WAIT`.
A task stopped as `WAIT` SHALL be told `wait-over` again after the last wait of `infraWait.backoff`, each time the session waits again.
A report of the session that is no wait SHALL end the stop.
`outcome=wait` SHALL be refused where `infraWait.backoff` is `[]`.

#### Scenario: A session waits
- **WHEN** ABC-42's session reports `outcome=wait`, "ledger db down: ping ledger.internal"
- **THEN** its status chip's hover says what is down and `next try HH:MM UTC`, and nobody is asked
- **AND** after the first wait jagt says `wait-over`; the session probes, and goes on, or reports `wait` again

#### Scenario: The dependency stays down
- **WHEN** the session answered `wait-over` with a wait 3 times
- **THEN** the task stops as `WAIT` with the session's last message as evidence

#### Scenario: The wait stop keeps asking
- **WHEN** a task stopped as `WAIT` reaches the last wait of `infraWait.backoff`
- **THEN** jagt says `wait-over` once, though the task is stopped
- **AND** where the session waits again, the next wake is due after the same wait

#### Scenario: The session goes on
- **WHEN** a task stopped as `WAIT` has its session report `IN_PROGRESS` with no wait
- **THEN** the stop is voided and the log says `stop voided by read` once, with the task, the trigger and `read=session report`

#### Scenario: Somebody else speaks first
- **WHEN** you type to the session, or a relay or an answer opens a new turn, before the wait is over
- **THEN** the wake of the old turn is never due

#### Scenario: A restart mid-wait
- **WHEN** jagt restarts while a wake is pending
- **THEN** the wake stands, and a `restarted` answered with a wait counts nothing
