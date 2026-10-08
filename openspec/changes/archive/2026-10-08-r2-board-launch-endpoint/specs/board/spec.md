## MODIFIED Requirements

### Requirement: One endpoint, one dialog
`GET /api/commands/{id}` SHALL serve any report and `POST` run any other; no command but the launch row's pickers
(`POST /api/tasks`) gets its own endpoint, a GET never starts a task. A report opens in a `<dialog>` over the board,
never a page. One about one task (`aboutOneTask`) gets no bar button, no tier 2 offer, and one line typing into that
session (`POST /api/tasks/say`): not a verb, the only control there.

#### Scenario: Closing
- **WHEN** the human presses Escape, the button, or the dimmed area
- **THEN** the dialog closes
