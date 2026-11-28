## ADDED Requirements

### Requirement: A role's report and the flows' evidence are jagt's own files
A role's report and the flows' evidence folder SHALL stay out of `git status`.
Both SHALL be archived when the task finishes.

#### Scenario: A report is written
- **WHEN** a checking role's report is written into a task's worktree
- **THEN** it does not show in `git status`, no commit of the task's work carries it, and the finish archive keeps it

#### Scenario: Flows leave evidence
- **WHEN** the scripted flows write their report and screenshots under `acceptance/`
- **THEN** none shows in `git status` and the finish archive keeps every file there
