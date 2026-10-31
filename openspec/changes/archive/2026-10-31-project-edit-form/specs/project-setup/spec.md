## ADDED Requirements

### Requirement: A project is edited from the board
`project edit <key>` SHALL open the add form filled with what `jagt.yml` holds for `<key>`. Its key is fixed.
Saving rewrites that project's block. The form reads its path without counting the project itself as another
one pointing there.

#### Scenario: Editing from the palette
- **WHEN** the human types `project edit billing`
- **THEN** the form opens titled for `billing`, with its path, branches and check command in the fields
