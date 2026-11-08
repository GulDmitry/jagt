## MODIFIED Requirements

### Requirement: Jobs that read the tracker
Each job SHALL do its one thing unasked: `ticket-prefetch` reads each working task's ticket once, kept beside `state.json` and booked as
`TICKET_TEXT`; `intake`, with `tracker.mode` `take` or `both`, starts a task on every item ready for one; `ticket-close`, with `close` or `both`,
closes a task whose item reached `doneStatus`.

#### Scenario: An item intake cannot start
- **WHEN** nothing routes ABC-42, and a later label names its project
- **THEN** each poll tries ABC-42 again; you are told once, a restart included, that intake cannot start it; the poll after the label starts it

#### Scenario: A finished item
- **WHEN** an item reaches `doneStatus` under `tracker.mode: close`
- **THEN** `ticket-close` closes its task

#### Scenario: A name that is no item key
- **WHEN** a name is read under `tracker.workflow` `jira` or `none`
- **THEN** `jira` takes `ABC-42`, `none` takes no name, and no read of an item is made for the rest

#### Scenario: No tracker
- **WHEN** `tracker.workflow` is `none` and a request is resumed on branch `ABC-42`
- **THEN** no tracker is asked about it

#### Scenario: A ticket read once
- **WHEN** a new stint or a restart begins for a task whose ticket was read
- **THEN** it reads none; a failed read is asked again only in the next stint
