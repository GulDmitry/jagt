## MODIFIED Requirements

### Requirement: One name is one tmux window
Opening a window SHALL first kill any same-named one; liveness SHALL be the child processes of `#{pane_pid}`. An
exited agent's window SHALL close after showing its tail 15s; an interactive shell MUST NEVER be left in it.
A missing session or server lists none; any other failed `list-windows` is unknown: a job or the watchdog decides nothing, a pressed verb is refused.

#### Scenario: The agent exits, or the windows cannot be listed
- **WHEN** the agent process in a window exits
- **THEN** the window shows its tail for 15s, holding no process, and closes, leaving no shell behind
- **WHEN** tmux cannot list the session's windows for any cause but a missing session or server
- **THEN** jagt opens, closes and renames no window, logs `tmux windows unreadable` with the cause, and opens no second window beside one that exists
