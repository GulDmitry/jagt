## MODIFIED Requirements

### Requirement: No re-brief on held threads
`TaskStops` and the session's open turn SHALL NOT re-brief a session on threads it was told to hold.

#### Scenario: Held thread
- **WHEN** a poll reads a held thread
- **THEN** no re-brief

### Requirement: An unchanged round is relayed once
`TaskStops.round` SHALL keep a digest of the last round brief relayed into a task. A brief equal to it SHALL NOT be
relayed again, whatever overwrote `task_context.md` in between. A `lift` SHALL forget it.

#### Scenario: A ship between two equal reads
- **WHEN** ABC-42 ships and the next poll reads the same threads as the round before
- **THEN** the sweep says the round is unchanged and no fresh session starts
