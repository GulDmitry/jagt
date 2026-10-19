## ADDED Requirements

### Requirement: A base-only move keeps the verdict
`MasterPanel.review` SHALL keep the last verdict, restamped, when it is ready and the task's own diff has the
identifier stored in it (`WorktreeInspection.ownDiffId`: the diff since the merge-base, line numbers and index lines
left out, hashed).
A verdict SHALL store no identifier when a role could not read the round, and a task holding an untracked file has none.

#### Scenario: Only the base moved
- **WHEN** ABC-42 is rebased onto a moved base and handed back with the same own diff
- **THEN** no role reads it; the last ready verdict stands

#### Scenario: The session replied to a not-ready round
- **WHEN** ABC-42 is handed back with the same own diff after a not-ready verdict
- **THEN** each role reads the round again, the reply beside it

#### Scenario: The task's own diff changed
- **WHEN** ABC-42 is handed back with any change of its own
- **THEN** each role reads the round as before
