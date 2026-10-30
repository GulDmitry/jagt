## MODIFIED Requirements

### Requirement: Jobs that keep the machine tidy and read the tracker
`intake` SHALL tell you once, restarts included, of an item it cannot start, and SHALL not read it again.

#### Scenario: An item nothing routes
- **WHEN** intake turns ABC-42 away and jagt restarts
- **THEN** no second banner says `intake cannot start ABC-42`, and ABC-42 is not read again
