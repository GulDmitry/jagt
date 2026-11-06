## MODIFIED Requirements

### Requirement: The gear holds the board's settings
One gear at the header's top right SHALL open a menu: the theme (`auto` follows `prefers-color-scheme`, `light` and `dark`
force it) and the language, one entry per catalog file present, named by that catalog. Both are kept in this browser alone and
applied before the first paint. English is the default and fills any key a catalog lacks; only the board's own UI text is
translated, never the backend's words or times. A colour keeps its one meaning in both themes. The legend has the gear's row.
#### Scenario: Dark and Polish chosen
- **WHEN** the human chooses `dark` and Polish on the gear and reloads the board
- **THEN** the page is dark with no flash and its header reads Polish, a key Polish lacks reads English, and `auto` clears the theme
