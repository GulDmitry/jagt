## ADDED Requirements

### Requirement: A team channel is a configured command
`notifications.channel.command` (argv, blank is off) runs once per event `notifications.channel.events` lists, JSON on stdin.
#### Scenario: Run, skip, fail
- **WHEN** `deployed` is listed and ABC-42 deploys
- **THEN** the command gets `{"event":"deployed","task":"ABC-42",...}` on stdin, null fields omitted; unset events: `request-opened`, `deployed`
- **WHEN** the event is not listed, or the command is blank
- **THEN** nothing runs
- **WHEN** the command exits non-zero, times out or cannot start
- **THEN** one WARN names channel, event, task, cause; nothing else changes. The secret stays in the command's environment
