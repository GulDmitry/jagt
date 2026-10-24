## MODIFIED Requirements

### Requirement: One name is one tmux window
Opening a window SHALL first kill any same-named one; liveness SHALL be the child processes of `#{pane_pid}`. An
exited agent's window SHALL close after showing its tail 15s; an interactive shell MUST NEVER be left in it.

#### Scenario: The agent exits
- **WHEN** the agent process in a window exits
- **THEN** the window shows its tail for 15s, holding no process, and closes, leaving no shell behind
