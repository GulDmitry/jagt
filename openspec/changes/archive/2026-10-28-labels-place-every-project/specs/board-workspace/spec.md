## ADDED Requirements

### Requirement: An item is placed in every project its labels name
An item whose labels or tracker project match configured projects SHALL be placed in every one of them (`ProjectRouting.projectFor`).
The router is asked only where no label matched and several projects are configured; it places the item in one project.

#### Scenario: Labels name two projects
- **WHEN** an item's labels match two configured projects
- **THEN** the task opens in both and the router is never asked

#### Scenario: No label matches
- **WHEN** no label matches and several projects are configured
- **THEN** the router reads the item and places it in one project, or in none
