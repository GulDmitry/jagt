## MODIFIED Requirements

### Requirement: Verbs come off the wire in groups
Per-task verbs SHALL come from `Move.actions()`, grouped by `TaskAction.Group`, a row per group.
A hint's text lives in `command/CommandReference` alone. `approve-plan` replaces the approval
dialog of the CLI's plan mode and the hint to tell the session to go. `add-repo` replaces the answer typed to a session
asking for a repository: it is offered, highlighted in place of `focus`, only while that request stands, and adds no mark.
A renamed verb still answers to its old spelling (`review`
runs `sweep`), unadvertised (`TaskAction.RENAMED`).

#### Scenario: A plan waits
- **WHEN** a task is at `PLAN_PENDING`
- **THEN** its top row offers `approve-plan`, and no other status does

#### Scenario: A session asks for a repository
- **WHEN** a session asks for a configured project it lacks
- **THEN** the card reads NEEDS INPUT and its top row offers `add-repo`, which no other question does

#### Scenario: Which buttons change something
- **WHEN** the human reads a card's buttons
- **THEN** the top row moves the task on; the bottom row only looks or restarts
