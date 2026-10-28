## MODIFIED Requirements

### Requirement: One name is one tmux window
Opening a window SHALL first kill any same-named one; liveness SHALL be the child processes of `#{pane_pid}`. An
exited agent's window SHALL close after showing its tail 15s; an interactive shell MUST NEVER be left in it.
A session or server that is not there lists no windows; any other failed `list-windows` is unknown, never "none".

#### Scenario: The agent exits
- **WHEN** the agent process in a window exits
- **THEN** the window shows its tail for 15s, holding no process, and closes, leaving no shell behind

#### Scenario: The windows cannot be listed
- **WHEN** tmux cannot list the session's windows for any cause but a missing session or server
- **THEN** jagt opens, closes and renames no window, logs `tmux windows unreadable` with the cause, and an open or a state read throws
- **AND** a window that exists is never opened a second time
