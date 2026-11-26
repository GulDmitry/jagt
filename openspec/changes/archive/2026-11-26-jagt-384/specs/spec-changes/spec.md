## ADDED Requirements

### Requirement: A fixed bug names its case
A fixed bug SHALL be written as one WHEN/THEN from the real timeline, then matched against the capability's scenarios by WHEN and state.
Its commit says `Case: existing <scenario>`, `Case: new <scenario>` or `Case: none`.
#### Scenario: A THEN that is itself wrong
- **WHEN** the code follows a scenario whose THEN is wrong
- **THEN** the spec changes first, and an owner question is asked if it contradicts the code
#### Scenario: A fix restores a stated scenario
- **WHEN** a defect breaks what a scenario's WHEN and THEN already say
- **THEN** the commit names that scenario as `Case: existing` and fixes the test that missed it
#### Scenario: A fix for a state the spec does not name
- **WHEN** the trigger has a scenario but the state or timing of the defect is not in it
- **THEN** the change adds a scenario for it before the code, and the commit says `Case: new`
#### Scenario: A fix to the build
- **WHEN** a defect lies in a Gradle file, a CI workflow or the harness
- **THEN** the commit says `Case: none` and owes no scenario

### Requirement: A fixed bug adds a chart region only for a condition of its own
A condition that must hold across moves and needs its own exit SHALL become a region or state of the TaskChart (decision 0110).
A wrong edge between existing states SHALL be one edge row.
#### Scenario: A flag that outlives moves
- **WHEN** a defect is a condition surviving several moves and ending only on its own event
- **THEN** the fix adds a region or state with that exit
#### Scenario: A move landing on the wrong state
- **WHEN** a defect is a move taking a wrong edge between two existing states
- **THEN** the fix changes one edge row

### Requirement: A Defect-family commit carries its postmortem
A commit with `Defect-family: <A-M>` SHALL carry `Case: new <scenario> | existing <scenario> | none`
and one trailer each of `Written: <introducing sha>`, `Uncaught: <why no test or review caught it>`
and `Unseen: <why nobody saw it running>`. Each answer names its durable fix: a rule, a test pattern, a guard or a log.
`FixedBugCaseFitness` SHALL fail a Defect-family commit after its cutoff without `Case`,
or touching main code with neither an openspec file nor `Case: existing`.
#### Scenario: A fix without a case
- **WHEN** a Defect-family commit has no `Case` trailer
- **THEN** the rule test names it
#### Scenario: A main-code fix naming a new case without a spec file
- **WHEN** a Defect-family commit touches main code, says `Case: new` and holds no openspec file
- **THEN** the rule test names it
