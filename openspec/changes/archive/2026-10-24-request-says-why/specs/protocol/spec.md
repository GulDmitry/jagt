## ADDED Requirements

### Requirement: A request says why
`update_agent_status` and `initialize_task` SHALL require `why`, one sentence of at most 120 characters, kept on the
step the call writes; missing or longer, the call is refused as `validation` naming `why` and 120.

#### Scenario: A reason too long
- **WHEN** a session reports with a `why` of 121 characters
- **THEN** it is refused as `validation` naming `why` and 120, and nothing moves
