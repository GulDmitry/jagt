## RENAMED Requirements
- FROM: `### Requirement: Position carries nothing`
- TO: `### Requirement: Position carries the tier`

## MODIFIED Requirements

### Requirement: Position carries the tier
`TaskViews` SHALL put the tasks waiting on the human first, then the rest in project order, each group in
registration order. A phase is a count above the grid, zeros included. One order control, `static/ui/order.js`, MUST
offer registration or alias; all else narrows: a filter over alias, id and title, typed in the filter box alone.
An alias is the lowest free number, so alias order puts a new task where a retired one sat.

#### Scenario: A status changes
- **WHEN** a task's tier changes to waiting on the human
- **THEN** its row moves into the band on top

#### Scenario: The rest
- **WHEN** no tier changes
- **THEN** the other rows keep their place, in project order

#### Scenario: The filter
- **WHEN** the human types in the filter box
- **THEN** rows narrow by alias, id or title, and no checkbox narrows them by tier
