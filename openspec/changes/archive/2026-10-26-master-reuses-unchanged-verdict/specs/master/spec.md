## MODIFIED Requirements

### Requirement: A base-only move keeps the verdict
`MasterPanel.review` SHALL keep the last ready or not-ready verdict while the hand-back keeps the inputs the verdict
read: the task's own diff identifier (`WorktreeInspection.ownDiffId`: the diff since the merge-base, line numbers and
index lines left out, hashed) and the kind of the report. The kept verdict answers the hand-back in front; none is
stored where a role could not read the round or an untracked file is held, and a question is never kept.

#### Scenario: Only the base moved
- **WHEN** ABC-42 is rebased onto a moved base and handed back with the same own diff and kind
- **THEN** no role reads it; the last verdict stands

#### Scenario: An unchanged hand-back is not reviewed twice
- **WHEN** ABC-42's session hands back `no_changes` again over the own diff its not-ready verdict read as `no_changes`
- **THEN** no role reads it; the verdict stands for the new turn

#### Scenario: A reply or a change of its own
- **WHEN** ABC-42 is handed back with any change of its own, or with another kind of report than the verdict read
- **THEN** each role reads the round again, the reply beside it
