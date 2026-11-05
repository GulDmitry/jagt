## MODIFIED Requirements

### Requirement: One surface, one projection
The board, served by the jar, SHALL be the one front-end. The New task form's `plan first` box reads "plan first / project unknown":
ticked with no project, Start sends a task with no project; a task waiting on a project `jagt.yml` lacks shows the human's
question naming the keys, with the add-project form.

#### Scenario: Start with no project
- **WHEN** the human presses Start with no box ticked and `plan first` unticked
- **THEN** the form says "Choose a project, or start with plan first" and sends nothing
- **WHEN** "plan first / project unknown" is ticked
- **THEN** Start sends the task with no project
