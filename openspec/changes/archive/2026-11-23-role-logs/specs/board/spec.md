## ADDED Requirements

### Requirement: Every enabled model role's log opens in the board's pane
Each model role that runs (the Master, acceptance, triage, one added later by its module) SHALL have one chip in the head, named by its id, and
one feed: an in-memory logback appender `ROLE_<id>` on its module's classes, read at `/api/roles/<id>/log` and announced by the `role-log` event.
A disabled role, or one a human holds, has no chip. jagt opens no tmux window for a role.

#### Scenario: A role's log
- **WHEN** the human presses the Master's chip
- **THEN** its log stands in the pane and follows new lines
- **WHEN** the human presses the chip of the open log
- **THEN** the pane hides it and the Master keeps working

#### Scenario: A role that does not run
- **WHEN** a role is disabled, or a human holds it
- **THEN** the head shows no chip for it
