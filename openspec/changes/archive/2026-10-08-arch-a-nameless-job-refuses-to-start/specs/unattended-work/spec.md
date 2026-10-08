## MODIFIED Requirements

### Requirement: Unattended work is a declared kind
Each unattended job SHALL be a `job/Job`: id, one line for the human, an interval or `null` for once at startup, `run()`.
`job/Jobs` ticks it, the one ticker: one thread per run, never overlapping, a throw booked against that job.

#### Scenario: Anything else running
- **WHEN** you ask whether anything else runs behind your back
- **THEN** the header shows the next unattended job, and one that failed; `jobs` has the detail

#### Scenario: A job names itself nothing
- **WHEN** a job declares a blank id
- **THEN** jagt refuses to start, naming the job's class
