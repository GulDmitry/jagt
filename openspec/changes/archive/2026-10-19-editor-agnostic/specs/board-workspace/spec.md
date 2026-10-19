## MODIFIED Requirements

### Requirement: Every job can run now
The jobs SHALL show as rows grouped by whom each serves, with the server's words, the last run and its error; the
next run sits in the row's hover. Each row's Run now sends `run <job>`.

#### Scenario: Running a job by hand
- **WHEN** the human presses Run now on jarwatch
- **THEN** it runs within a second, and the toast says so
