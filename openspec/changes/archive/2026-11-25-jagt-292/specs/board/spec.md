## MODIFIED Requirements

### Requirement: Pushed, not polled, acted on by data
The board SHALL NOT poll: `TaskEventStream` forwards `StateWritten` as payload-free SSE at
`/api/events`, and a role's new log line as `role-log` naming the role, beside a 20s beat. Each connect reads the board once.
A card carries `data-action`, never a closure, so a rebuilt card cannot act for its old task.
#### Scenario: The backend goes away
- **WHEN** the push connection drops, or misses two beats; **THEN** the board dims and says `backend unreachable` until it reconnects
- **AND** every control that writes to the backend is inert; the filter, theme and language, local to the browser, still answer

### Requirement: The styleguide draws every state
`styleguide.html` SHALL render each component in each state its `styleguide/` fixtures name, with the modules the
board renders. A component reads no module state and imports nothing from `ui/`: everything it shows arrives in its props.
#### Scenario: Opening the styleguide
- **WHEN** a human opens `/styleguide.html`; **THEN** every card state stands as one card under its name, and the page throws nothing
