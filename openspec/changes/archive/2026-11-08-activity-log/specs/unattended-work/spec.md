## REMOVED Requirements

### Requirement: The activity report reads jagt's own log
**Reason**: `activity` is the task activity log below; it no longer reads the log file.
**Migration**: Read `logging.file.name` directly for jagt's own unattended entries.

## ADDED Requirements

### Requirement: The activity report is the task activity log
`command/ActivityReport` SHALL list every status change of every live and every finished task, newest first: time (UTC), task, from and to
status, origin. A kind and a project key after the verb filter it. A report line naming a task still on the board SHALL open that task.

#### Scenario: Live and finished tasks merge
- **WHEN** finished ABC-1 changed at 03:00 and live ABC-2 at 02:00
- **THEN** ABC-1's line stands above ABC-2's
- **AND** a finished task ends in `done`, stamped at its retirement, and at most the newest 200 lines show

#### Scenario: A filter
- **WHEN** the human types `activity alpha stopped`
- **THEN** only changes of `alpha` tasks into `CI_FAILED` or `DEPLOY_CONFLICT` are listed
- **AND** `created` is a task's first step, `shipped` is `SHIPPING`, `deployed` is `DEPLOYED`, `done` is `DONE`
- **AND** `activity shipped done` answers the usage line, and a filter matching nothing says which it was

#### Scenario: A line opens its task
- **WHEN** the human presses `ABC-2` in a line while ABC-2 is on the board
- **THEN** the report closes and ABC-2 opens
- **AND** a finished task's line stays text
