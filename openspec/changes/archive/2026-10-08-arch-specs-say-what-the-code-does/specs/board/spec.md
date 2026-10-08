## MODIFIED Requirements

### Requirement: One surface, one projection
The board, served by the jar, SHALL be the one front-end. Its projection is `flow/Move` + `flow/TaskView`, built by
`service/TaskViews`, rendered by `/api/tasks`; gate and button read one `FlowRules.allows`;
`TaskViews.snapshot()` reads the configuration once per render. A second surface MUST add no second answer to a board
question. Stopping the backend is no verb.

#### Scenario: Ship
- **WHEN** `ship` runs
- **THEN** it asks its button's `FlowRules`

### Requirement: Pushed, not polled, acted on by data
The board SHALL NOT poll: `StateService.onChange` is the one event, `TaskEventStream` forwarding it as SSE at
`/api/events` with no payload, which a second serialization could contradict. A periodic tick survives for the ACTIVE
clock. A card carries `data-action`, never a closure: `ui/render` holds the one delegated listener on the grid, so a
card rebuilt under the pointer cannot act for its old task.

#### Scenario: The clock reset
- **WHEN** a card said 17h and the restarted agent shows 0m
- **THEN** that is correct: the clock is time in that status, and a fresh session reports itself anew
