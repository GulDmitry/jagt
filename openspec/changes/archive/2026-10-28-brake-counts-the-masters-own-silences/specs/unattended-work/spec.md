## MODIFIED Requirements

### Requirement: `act` stops itself after three bad endings
Three tasks in a row the Master moved in `act` ending silent after its own last move, in a deploy conflict or
reverted SHALL stop `act` (`MasterBrake`); a deploy resets the count.

#### Scenario: Three tasks it moved end badly in a row
- **WHEN** the third one is stamped silent, conflicts on deploy or is reverted
- **THEN** it only judges, across restarts, until you press the header's `act stopped`

#### Scenario: Only a silence after its own move counts
- **WHEN** the Master ships ABC-42 and its session falls silent
- **THEN** a strike is counted
- **AND** where you converse in ABC-42's window and its session idles, none is
