## MODIFIED Requirements

### Requirement: Unattended work is a declared kind
Each unattended job SHALL be a `job/Job`: id, one line for the human, an interval or `null` for once at startup, `run()`. `job/Jobs` schedules each on
the shared `TaskScheduler` once ready: never overlapping itself, the next run stamped from the start of the last, a throw booked against that job.
A failure a job handles itself SHALL be reported through `JobFaults.faulted(cause)`: the job counts as failed with the first cause of its run until its
next run begins. A catch of a broad exception in `job/` that neither reports nor rethrows MUST fail the fitness `job-catches-report`.
A job failing with one cause run after run logs ERROR once, DEBUG after, and ERROR with `repeats` at most hourly.
#### Scenario: Anything else running
- **WHEN** you ask whether anything else runs behind your back
- **THEN** the header shows the next unattended job, and one that failed; `jobs` has the detail

#### Scenario: A job names itself nothing, or outlasts its interval
- **WHEN** a job declares a blank id
- **THEN** jagt refuses to start, naming the job's class
- **AND** a run lasting longer than the job's interval is followed the moment it ends, never beside it
#### Scenario: A job fails every minute
- **WHEN** a job fails with the same cause on every tick for over an hour
- **THEN** the log holds its first ERROR and a repeat ERROR carrying the runs since

#### Scenario: A job handles a failure itself
- **WHEN** a job catches a failure or reads a failed answer and reports it
- **THEN** the head's jobs chip and `jobs` name that job with the cause
- **AND** its next run that reports nothing clears it
