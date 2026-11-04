## MODIFIED Requirements

### Requirement: Jobs that read the tracker
Each of these jobs SHALL do its one thing unasked:
- `ticket-prefetch`: reads each working task's ticket once, for the Master's next review of it.
- `intake`: with `tracker.mode` `take` or `both`, starts a task on every item ready for one. One it cannot
  is tried again every poll, and told once.
- `ticket-close`: with `tracker.mode` `close` or `both`, closes a task whose item reached `doneStatus`.

A name is an item key only as `tracker.workflow` says: `jira` takes `ABC-42`, `none` takes no name, and no read of an item is made for the rest.

#### Scenario: An item intake cannot start
- **WHEN** nothing routes ABC-42, and a later label names its project
- **THEN** you are told once that intake cannot start ABC-42, a restart included, and the next poll after the label starts it

#### Scenario: A finished item
- **WHEN** an item reaches `doneStatus` under `tracker.mode: close`
- **THEN** `ticket-close` closes its task

#### Scenario: No tracker
- **WHEN** `tracker.workflow` is `none` and a request is resumed on branch `ABC-42`
- **THEN** no tracker is asked about it
