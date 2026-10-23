## MODIFIED Requirements

### Requirement: The brief is a file you may copy
What jagt tells a session at a step SHALL be that step's facts, then its `## <step>` section of `session-brief.md`
beside `jagt.yml`, copied from `session-brief.md.dist`; a section the copy lacks SHALL be read from the `.dist`, and
at one left empty the session SHALL hear the facts alone (`StepRelay`).

#### Scenario: Nothing copied
- **WHEN** no `session-brief.md` exists
- **THEN** every step says what `session-brief.md.dist` says

#### Scenario: A step left empty
- **WHEN** your copy holds `## plan` with nothing under it
- **THEN** an approved plan's session hears who read it and that it holds, and no more
