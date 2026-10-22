## MODIFIED Requirements

### Requirement: Verbs come off the wire in groups
Per-task verbs SHALL come from `Move.actions()`, grouped by `TaskAction.Group`, a row per group.
A hint's text lives in `command/CommandReference` alone. `approve-plan` replaces the approval
dialog of the CLI's plan mode and the hint to tell the session to go. A renamed verb still answers to its old spelling (`review`
runs `sweep`), unadvertised (`TaskAction.RENAMED`).

#### Scenario: A plan waits
- **WHEN** a task is at `PLAN_PENDING`
- **THEN** its top row offers `approve-plan`, and no other status does

#### Scenario: Which buttons change something
- **WHEN** the human reads a card's buttons
- **THEN** the top row moves the task on; the bottom row only looks or restarts

### Requirement: The press log notes two sightings
The board SHALL log `shown:required` when a task's attention BECOMES required, and `shown:jobs-failed` when the
failed-jobs chip appears. Both SHALL go through a press's batch and switch. A sighting is logged once per
change, never per render, and the first board painted only seeds. `shown:required` carries the task's status and
`shown:jobs-failed` null; neither carries a task id or text.

#### Scenario: A sighting
- **WHEN** a task's attention turns REQUIRED, or the hidden failed-jobs chip appears, on a board already painted
- **THEN** one line holds `shown:required` and its status, or `shown:jobs-failed` and null; further renders add none
