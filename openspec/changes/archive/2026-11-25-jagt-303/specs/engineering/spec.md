## ADDED Requirements

### Requirement: The classes the fixes return to are named
`hotSpots` SHALL name the ten main classes most fixed in 30 days and fail when the top count differs from `hotSpots.max`.
#### Scenario: The top count moves or the history is short
- **WHEN** a main class holds more fix commits (`Defect-family:` but `L`) than `hotSpots.max` **THEN** `hotSpots` fails naming it and its count
- **AND** a top count below `hotSpots.max` fails asking to lower it
- **AND** a checkout holding less than 30 days of history fails naming the missing history
