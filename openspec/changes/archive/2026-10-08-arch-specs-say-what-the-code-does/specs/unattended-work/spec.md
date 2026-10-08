## ADDED Requirements

### Requirement: Jobs that keep the machine tidy
Each of these jobs SHALL do its one thing unasked:
- `jarwatch`: each minute, tells you the jar this process runs from was rebuilt underneath it.
- `idecleanup`: each minute, drops worktrees that are gone from the editor's recent-projects list.
- `orphanscan`: at startup, deletes jagt's own worktree residue and warns about leftovers holding work or secrets.

#### Scenario: A husk of a worktree
- **WHEN** a worktree directory jagt cut has lost its checkout and holds only the editor's files
- **THEN** `orphanscan` deletes it at startup

### Requirement: Jobs that read the tracker
Each of these jobs SHALL do its one thing unasked:
- `ticket-prefetch`: reads each working task's ticket once, for the Master's next review of it.
- `intake`: with `tracker.mode` `take` or `both`, starts a task on every item ready for one.
- `ticket-close`: with `tracker.mode` `close` or `both`, closes a task whose item reached `doneStatus`.

#### Scenario: The work landed
- **WHEN** an item reaches `doneStatus` under `tracker.mode: close`
- **THEN** `ticket-close` closes its task

### Requirement: A job runs now when asked
`run <job>` SHALL make one job due at the next tick instead of at its interval.

#### Scenario: Running a job now
- **WHEN** you run `run orphanscan`
- **THEN** it runs at the next tick; a job already running, or one nobody declared, is refused
