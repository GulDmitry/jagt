## MODIFIED Requirements

### Requirement: Pushed, not polled, acted on by data
The board SHALL NOT poll: `TaskEventStream` forwards `StateService.onChange`, the one event, as payload-free SSE at
`/api/events` beside a 20s beat. Each connect reads the board once. A periodic tick survives for the ACTIVE clock,
time in that status. A card carries `data-action`, never a closure, so a card rebuilt under the pointer cannot act for its old task.

#### Scenario: The backend goes away
- **WHEN** the push connection drops, or misses two beats
- **THEN** the board dims, takes no input and says `backend unreachable` until it reconnects
