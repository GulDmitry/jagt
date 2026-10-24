## MODIFIED Requirements

### Requirement: No re-brief on held threads
`LoopGuard` and the session's open turn SHALL NOT re-brief a session on threads it was told to hold.

#### Scenario: Held thread
- **WHEN** a poll reads a held thread
- **THEN** no re-brief
