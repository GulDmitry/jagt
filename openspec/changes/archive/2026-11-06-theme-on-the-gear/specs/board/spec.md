## ADDED Requirements

### Requirement: The gear holds the board's settings
One gear at the header's top right SHALL open a menu choosing the theme: `auto` follows `prefers-color-scheme`,
`light` and `dark` force it. The choice is kept in this browser alone and applied before the first paint. A colour
keeps its one meaning in both themes. The legend has the gear's row.
#### Scenario: Dark chosen
- **WHEN** the human chooses `dark` on the gear and reloads the board
- **THEN** the page is dark with no flash, and `auto` clears the choice
