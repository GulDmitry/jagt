## MODIFIED Requirements

### Requirement: A cold prompt starts fresh
A session may stand idle for `orchestrator.agent.cacheLifetime` (default `1h`, `0` never). A prompt typed after that
SHALL be written under the brief in `task_context.md` and start a fresh session (`AgentSessions.relayUnderCold`).
Only then SHALL it be blocked, with one line saying where it went. Idle counts from `Session.idleSince`, the later
of the session's halt and last sign, never while its turn is open or where it has signed nothing.
The record is persisted, so a restart of jagt never reads a cold session as warm.
Starting a fresh session starts the record over, so nothing the old session signed speaks for the new one.
The first read after the start stamps its start.

#### Scenario: An idle session gets a prompt
- **WHEN** a prompt comes after the cache lifetime
- **THEN** a fresh session holds it, and the prompt is blocked

#### Scenario: jagt restarted while the session idled
- **WHEN** jagt restarted while the session idled past the lifetime
- **THEN** a prompt still goes to a fresh session

#### Scenario: The fresh session's first prompt is never relayed again
- **WHEN** a prompt arrives while its session's turn is open, as a fresh session's first one does
- **THEN** it reaches the model
- **WHEN** a prompt arrives before the fresh session's first hook
- **THEN** it passes, the old session's idle stamp being gone
- **WHEN** no hook has landed within the stale limit of the first read after the start, however old the previous transcript
- **THEN** the fresh session is not QUIET, and no stopped banner is raised for it
