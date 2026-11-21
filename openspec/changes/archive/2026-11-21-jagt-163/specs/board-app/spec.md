## ADDED Requirements

### Requirement: The board passes an accessibility check
`boardTest` SHALL run axe-core against the board, its new-task form, its command palette, the styleguide and the
login page, and fail on any serious or critical violation, naming the rule and the element.

#### Scenario: A serious violation
- **WHEN** a board view carries a serious or critical axe-core violation
- **THEN** `boardTest` fails naming the rule and the element

#### Scenario: A row's name
- **WHEN** a task row prints its alias, title and status
- **THEN** its accessible name is that text, and the checks, the stop and the change it does not print are its description
