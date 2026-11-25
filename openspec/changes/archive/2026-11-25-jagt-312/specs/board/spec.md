## MODIFIED Requirements

### Requirement: Every enabled model role's log opens in the board's pane
Each model role that runs SHALL have one chip in the head, named by its id: the Master, acceptance, triage and any added later.
Each has one feed, an in-memory logback appender `ROLE_<id>` on its module's classes, read at `/api/roles/<id>/log`, announced by `role-log`.
A disabled role, or one a human holds, has no chip. jagt opens no tmux window for a role.
#### Scenario: A role's log
- **WHEN** the human presses the Master's chip; **THEN** its log stands in the pane, newest entry on top, and follows new entries
- **WHEN** the human presses the chip of the open log; **THEN** the pane hides it and the Master keeps working
#### Scenario: An empty log on an empty board
- **WHEN** the human opens the Master's log on a board with no tasks and nothing logged
- **THEN** the empty line stands, in the log's style, where the newest entry would; no task filter, count or key hint shows
- **WHEN** the Master logs its first entry; **THEN** it replaces the empty line at the top
#### Scenario: A role that does not run
- **WHEN** a role is disabled, or a human holds it; **THEN** the head shows no chip for it

### Requirement: Every log view lists newest first
The Master's and every role's log, the session log and the `tail` report SHALL render through one `LogLines` component.
One block per entry, newest on top, a 6px gap; time (`HH:MM:SS` UTC) and INFO level `--muted`, WARN and ERROR `--danger`, message `--text`.
The legend has a row for the muted time and one for a danger entry. The session tab's `.screen` stays in terminal order.
#### Scenario: A log
- **WHEN** the human opens the Master's log holding a refusal, a move and a note
- **THEN** the newest entry is on top, a visible gap separates entries, the refusal is `--danger` and each time is muted
- **WHEN** entries arrive while the human has scrolled down; **THEN** the entries being read stay in view
