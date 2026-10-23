## ADDED Requirements

### Requirement: The brief is a file you may copy
What jagt tells a session at a step SHALL be that step's `## <step>` section of `session-brief.md` beside `jagt.yml`,
copied from `session-brief.md.dist`; a section the copy lacks SHALL be read from the `.dist`, and one left empty
SHALL tell the session nothing at that step (`SessionBrief`).

#### Scenario: Nothing copied
- **WHEN** no `session-brief.md` exists
- **THEN** every step says what `session-brief.md.dist` says

#### Scenario: A step left empty
- **WHEN** your copy holds `## deploy` with nothing under it
- **THEN** a deploy asks the session nothing
