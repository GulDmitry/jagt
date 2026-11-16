## ADDED Requirements

### Requirement: The image keeps its tmux across a jagt restart
The shipped image SHALL run an init as PID 1 and jagt under a relaunch loop.
When jagt's process exits, the loop SHALL start it again and the container's tmux server SHALL keep running.
A termination signal to the container SHALL stop jagt and the loop promptly.

#### Scenario: The JVM dies
- **WHEN** jagt's JVM inside the container is killed while a session's tmux window is open
- **THEN** jagt starts again in the same container and the window is still listed

#### Scenario: The container is stopped
- **WHEN** the container receives SIGTERM
- **THEN** jagt stops and the container exits without the loop starting jagt again
