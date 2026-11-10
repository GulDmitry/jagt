## MODIFIED Requirements

### Requirement: Verbs come off the wire in groups
Per-task verbs SHALL come from `Move.actions()`, a row per `TaskAction.Group`; a hint's text lives in `command/CommandReference` alone.
A renamed verb still answers to its old spelling, unadvertised (`TaskAction.RENAMED`). A hint names the Session tab or a board verb, never `focus`.

#### Scenario: Which buttons change something
- **WHEN** the human reads a card's buttons
- **THEN** the top row moves the task on; the bottom row only looks or restarts
