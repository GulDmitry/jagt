## MODIFIED Requirements

### Requirement: A pipeline that died on infrastructure is re-run
`InfraRetryJob` (every minute) SHALL re-run a pipeline whose armed retry is due. The task MUST be open and not stopped, its checks still
red, and the host's failed jobs still all infrastructure. The armed retry and those sent SHALL live on the task, so a restart
keeps them; an overdue one fires on the first tick. A sent retry SHALL be a history step by `jagt`, counted toward the hour's ceiling.
A due retry the ceiling refuses stops the task as `PACE`.
A checks read or approval SHALL be kept with the pushed commit it was read on; read on any other commit than the
request's current one it reads as unknown, and a push disarms the retry armed for the commit before.

#### Scenario: The session pushes while a retry is armed
- **WHEN** a red read is recorded on commit A and the task's head moves to B
- **THEN** its checks read unknown, no retry is due, and the next read on B records afresh

#### Scenario: A restart mid-wait
- **WHEN** jagt restarts with a retry armed and overdue
- **THEN** the first tick re-reads the reasons and re-runs it

#### Scenario: The task closed while it waited
- **WHEN** ABC-42 is done before its retry is due
- **THEN** the job drops the entry and sends nothing
