## MODIFIED Requirements

### Requirement: A cold prompt starts fresh
A session may stand idle for `orchestrator.agent.cacheLifetime` (default `1h`, `0` never). A prompt typed after that
SHALL be written under the brief in `task_context.md` and start a fresh session (`AgentSessions.relayUnderCold`).
Only then SHALL it be answered with one line, a hand-over: the message moved to a fresh session of the task, which is
working on it now, and the idle threshold.

#### Scenario: An idle session gets a prompt
- **WHEN** a prompt comes after the cache lifetime
- **THEN** a fresh session holds it, and the line shown says so, names the threshold and no failure

#### Scenario: jagt cannot relay
- **WHEN** the relay or the fresh start fails, or jagt is down
- **THEN** the prompt reaches the old session and a warning with its cause is logged

#### Scenario: The prompt passes
- **WHEN** the session was working, is within the cache lifetime, or no task owns it
- **THEN** the prompt passes
- **WHEN** it starts with `/` or holds a pasted image
- **THEN** it passes whatever the idle time
