## MODIFIED Requirements

### Requirement: A waited-on command leaves no process behind
A command jagt waits on (`ProcessRunner.run`) SHALL be killed with every descendant when its timeout passes, when the
wait is interrupted and when jagt stops. A detached launch SHALL be left running: it outlives jagt by design.

#### Scenario: A headless run times out
- **WHEN** a command times out while a child it spawned is still running
- **THEN** the command and the child are both killed

#### Scenario: jagt stops mid-run
- **WHEN** jagt stops while it waits on a command
- **THEN** the command is killed, and its caller is told it was stopped (`Processes.Stopped`), never an exit code to judge
