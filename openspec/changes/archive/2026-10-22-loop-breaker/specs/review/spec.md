## ADDED Requirements

### Requirement: An unchanged round is relayed once
`LoopGuard.round` SHALL keep a digest of the last round brief relayed into a task, so a brief equal to it is not
relayed again, whatever instruction overwrote `task_context.md` in between. A `lift` SHALL forget it.

#### Scenario: A ship between two equal reads
- **WHEN** ABC-42 ships and the next poll reads the same threads as the round before
- **THEN** the sweep says the round is unchanged and no fresh session starts
