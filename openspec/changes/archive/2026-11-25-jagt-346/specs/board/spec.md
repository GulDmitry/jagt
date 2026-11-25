## MODIFIED Requirements

### Requirement: Every enabled model role's log opens in the board's pane
Each model role that runs SHALL have one chip in the head, named by its id: the Master, acceptance, triage and any added later.
Each has one feed, an in-memory logback appender `ROLE_<id>` on its module's classes, read at `/api/roles/<id>/log`, announced by `role-log`.
A disabled role, or one a human holds, has no chip. jagt opens no tmux window for a role.
#### Scenario: A role's log
- **WHEN** the human presses the Master's chip; **THEN** its log stands in the pane, newest entry on top, and follows new entries
- **WHEN** the human presses the chip of the open log; **THEN** the pane hides it and the Master keeps working
- **WHEN** a role's log stands; **THEN** the pane's back row reads `‹ all tasks · <role> log`
#### Scenario: An empty log on an empty board
- **WHEN** the human opens the Master's log on a board with no tasks and nothing logged
- **THEN** the empty line stands, in the log's style, where the newest entry would; no task filter, count, key hint, `No tasks yet` or phase bar shows
- **WHEN** the Master logs its first entry; **THEN** it replaces the empty line at the top
#### Scenario: A role that does not run
- **WHEN** a role is disabled, or a human holds it; **THEN** the head shows no chip for it
