## ADDED Requirements

### Requirement: Three timers remain
Three timers SHALL remain, counted by `board-timers`. `ui/controls.js` repaints every 15s, for the ACTIVE clock. `ui/tabs.js` re-reads the
peek and the Session tab's window list every 5s. `ui/followup.js` repaints an open report every 3s, for 5 minutes at most after a typed line.
#### Scenario: A typed line gets no answer
- **WHEN** a line typed at an open report gets no answer within 5 minutes
- **THEN** the board stops repainting and a toast says the session is silent

## REMOVED Requirements

### Requirement: Every window jagt opens is on the Session tab
**Reason**: The Session tab is the session-stream capability's.
**Migration**: Moved unchanged to session-stream.

## MODIFIED Requirements

### Requirement: Pushed, not polled, acted on by data
The board SHALL NOT poll: `TaskEventStream` forwards `StateWritten` as payload-free SSE at
`/api/events`, and a role's new log line as `role-log` naming the role, beside a 20s beat. Each connect reads the board once.
A card carries `data-action`, never a closure, so a rebuilt card cannot act for its old task.
#### Scenario: The backend goes away
- **WHEN** the push connection drops, or misses two beats; **THEN** the board dims, takes no input and says `backend unreachable` until it reconnects
