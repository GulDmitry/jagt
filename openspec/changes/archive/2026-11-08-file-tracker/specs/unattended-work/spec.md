## ADDED Requirements

### Requirement: A Markdown file may be the tracker
With `tracker.workflow: file`, jagt SHALL read the items of the Markdown file `tracker.file` itself, without a model.
A top-level `- [ ] **ABC-42 — Title**` line is an item, `[ ]` reading as `open` and `[x]` as `done`; its body runs to the
next top-level list line or heading. A key the file holds is answered from it, any other reference is read as before,
and startup refuses `file` without `tracker.file`.

#### Scenario: An unchecked item
- **WHEN** the file holds `- [ ] **ABC-42 — A title**` and `startStatus` is `open`
- **THEN** intake takes ABC-42

#### Scenario: A checked item
- **WHEN** ABC-42 is checked `[x]` and `doneStatus` is `done`
- **THEN** its task closes

#### Scenario: An unreadable file
- **WHEN** the file is missing or unreadable
- **THEN** the poll is skipped with a warning naming the cause, and no item is taken as gone

#### Scenario: A reference the file does not hold
- **WHEN** you run `do <url>` or name a key the file lacks
- **THEN** it is read through your MCP servers as without the file tracker
