## ADDED Requirements

### Requirement: A closed task refuses a late verdict
A task closed by you or by `ticket-close` SHALL end its check: a verdict arriving afterwards is refused and changes nothing.

#### Scenario: Closed while QA reads
- **WHEN** ABC-42 is closed while QA reads it and the verdict arrives
- **THEN** the verdict is refused and no round or question is raised
