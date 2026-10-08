## MODIFIED Requirements

### Requirement: Pushed, not polled, acted on by data
The board SHALL NOT poll: `StateService.onChange` is the one event, `TaskEventStream` forwarding it as SSE with no
payload, which a second serialization could contradict. Each connect reads the board once. A periodic tick survives for
the ACTIVE clock. A card carries `data-action`, never a closure: `ui/render` holds the one delegated listener on the
grid, so a card rebuilt under the pointer cannot act for its old task.

#### Scenario: The clock reset
- **WHEN** a card said 17h and the restarted agent shows 0m
- **THEN** that is correct: the clock is time in that status

#### Scenario: The backend goes away
- **WHEN** the push connection drops
- **THEN** the board dims, takes no input and says `backend unreachable` until it reconnects
