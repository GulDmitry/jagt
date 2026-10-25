## MODIFIED Requirements

### Requirement: Unattended work is a declared kind
Each unattended job SHALL be a `job/Job`: id, one line for the human, an interval or `null` for once at startup, `run()`.
`job/Jobs` schedules each on the shared `TaskScheduler` once ready: never overlapping itself, the next run
stamped from the start of the last, a throw booked against that job.

#### Scenario: Anything else running
- **WHEN** you ask whether anything else runs behind your back
- **THEN** the header shows the next unattended job, and one that failed; `jobs` has the detail

#### Scenario: A job names itself nothing
- **WHEN** a job declares a blank id
- **THEN** jagt refuses to start, naming the job's class

#### Scenario: A job outlasts its interval
- **WHEN** a run lasts longer than the job's interval
- **THEN** the next run starts the moment it ends, never beside it

### Requirement: A job runs now when asked
`run <job>` SHALL make one job due at once instead of at its interval.

#### Scenario: Running a job now
- **WHEN** you run `run orphanscan`
- **THEN** it runs at once; a job already running, or one nobody declared, is refused
