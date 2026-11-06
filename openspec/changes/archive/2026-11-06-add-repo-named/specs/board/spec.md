## MODIFIED Requirements

### Requirement: Verbs come off the wire in groups
Per-task verbs SHALL come from `Move.actions()`, a row per `TaskAction.Group`; a hint's text lives in
`command/CommandReference` alone. `approve-plan` replaces the CLI plan mode's approval dialog and its go-ahead hint.
`add-repo` replaces the answer typed to a session asking for a repository: highlighted in place of `focus` while that
request stands, no mark. Typed `add-repo <ticket> <project>` it names the repository, with no button; the project is an
optional parameter of the action's one endpoint. A renamed verb still answers to its old spelling (`review` runs `sweep`), unadvertised (`TaskAction.RENAMED`).
#### Scenario: A plan or a repository waits
- **WHEN** a task is at `PLAN_PENDING`, or its session asked for a configured project it lacks
- **THEN** its top row offers `approve-plan`, or `add-repo` on the NEEDS INPUT card; no other status or question does
#### Scenario: Which buttons change something
- **WHEN** the human reads a card's buttons
- **THEN** the top row moves the task on; the bottom row only looks or restarts
