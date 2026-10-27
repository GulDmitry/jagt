## MODIFIED Requirements

### Requirement: A cold prompt starts fresh
A session may stand idle for `orchestrator.agent.cacheLifetime` (default `1h`, `0` never). A prompt typed after that
SHALL be written under the brief in `task_context.md` and start a fresh session (`AgentSessions.relayUnderCold`).
Only then SHALL it be blocked, with one line saying where it went. Idle counts from the turn's end in the session's own
transcript. A restart of jagt or a lost hook never reads a cold session as warm.

#### Scenario: An idle session gets a prompt
- **WHEN** a prompt comes after the cache lifetime
- **THEN** a fresh session holds it, and the prompt is blocked

#### Scenario: jagt restarted while the session idled
- **WHEN** jagt restarted while the session idled past the lifetime
- **THEN** a prompt still goes to a fresh session

#### Scenario: jagt cannot relay
- **WHEN** the relay or the fresh start fails, or jagt is down
- **THEN** the prompt reaches the old session and a warning with its cause is logged

#### Scenario: The prompt passes
- **WHEN** the session was working, is within the cache lifetime, or no task owns it
- **THEN** the prompt passes
- **WHEN** it starts with `/` or holds a pasted image
- **THEN** it passes whatever the idle time
