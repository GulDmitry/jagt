## MODIFIED Requirements

### Requirement: A base-only move keeps the verdict
`MasterPanel.review` SHALL keep the last verdict, not ready too, while the hand-back keeps the inputs it read: own diff
identifier (`WorktreeInspection.ownDiffId`), report kind, and a digest of the human lines, notes and probes, and the
ticket's own digest, which is no input while no read of the ticket stands. It answers the hand-back in front; a
question, an unread round or an untracked file is never kept.

#### Scenario: An unchanged hand-back is not reviewed twice
- **WHEN** ABC-42 is rebased onto a moved base, or its session hands back `no_changes` again, over the same inputs
- **THEN** no role reads it; the last verdict stands for the new turn

#### Scenario: A reply or a change of its own
- **WHEN** ABC-42 is handed back with any change of its own, or with another report kind, or a new human line or note
- **THEN** each role reads the round again, the reply beside it

#### Scenario: A restart forgot the ticket
- **WHEN** jagt restarted since ABC-42's verdict read the ticket, and the hand-back holds the same inputs otherwise
- **THEN** no role reads it; the last verdict stands

#### Scenario: The ticket changed
- **WHEN** the ticket read for ABC-42's hand-back differs from the one the verdict read, or the verdict read none
- **THEN** each role reads the round again
