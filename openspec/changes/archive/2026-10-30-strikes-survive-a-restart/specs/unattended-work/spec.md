## MODIFIED Requirements

### Requirement: `act` stops itself after three bad endings
Three tasks in a row the Master moved in `act` ending silent, in a deploy conflict or reverted SHALL stop `act`
(`MasterBrake`). A silence counts after its own last move; a deploy resets the count, a restart does not.

#### Scenario: Three tasks it moved end badly in a row
- **WHEN** the third one is stamped silent, conflicts on deploy or is reverted
- **THEN** it only judges, across restarts, until you press the header's `act stopped`
- **AND** a strike counts when the Master ships ABC-42 and its session falls silent, none where you converse in its window and it idles


