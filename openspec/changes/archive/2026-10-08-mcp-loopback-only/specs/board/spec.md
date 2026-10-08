## MODIFIED Requirements

### Requirement: One surface, one projection
The board, served by the jar, SHALL be the one front-end. Its projection is `flow/Move` + `flow/TaskView`, built by
`service/TaskViews`, rendered by `/api/tasks`; gate and button read one `FlowRules.allows`;
`TaskViews.snapshot()` reads the configuration once per render. A second surface MUST add no second answer to a board
question. Stopping the backend is no verb.

#### Scenario: Ship
- **WHEN** `ship` runs
- **THEN** it asks its button's `FlowRules`

### Requirement: Loopback only
The board SHALL bind loopback and ask no password; `surface/board/LoopbackFilter` SHALL refuse a foreign Host or
Origin, and `/mcp` anything but JSON.

#### Scenario: A second machine
- **WHEN** jagt runs with `--server.address=0.0.0.0`
- **THEN** the board is reachable elsewhere, still without a password

#### Scenario: Another site posts a verb
- **WHEN** a page elsewhere, or a rebound name, sends one
- **THEN** it is refused 403
