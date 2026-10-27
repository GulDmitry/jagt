# cold-prompts Specification

## Purpose
A prompt typed into a session idle past its cache costs the whole context again.
jagt sends it to a fresh session and keeps that session's notes current.

## Requirements

### Requirement: A cold prompt starts fresh
A session may stand idle for `orchestrator.agent.cacheLifetime` (default `1h`, `0` never). A prompt typed after that
SHALL be written under the brief in `task_context.md` and start a fresh session (`AgentSessions.relayUnderCold`).
Only then SHALL it be blocked, with one line saying where it went. Idle counts from the turn's end.

#### Scenario: An idle session gets a prompt
- **WHEN** a prompt comes after the cache lifetime
- **THEN** a fresh session holds it, and the prompt is blocked

#### Scenario: jagt cannot relay
- **WHEN** the relay or the fresh start fails, or jagt is down
- **THEN** the prompt reaches the old session and a warning with its cause is logged

#### Scenario: The prompt passes
- **WHEN** the session was working, is within the cache lifetime, or no task owns it
- **THEN** the prompt passes
- **WHEN** it starts with `/` or holds a pasted image
- **THEN** it passes whatever the idle time

### Requirement: A cold prompt carries what the old session knew
Beneath the prompt, `task_context.md` SHALL carry, gathered without a model: the last report or open question, the commits
and changed files since the base (40 lines each), and the old transcript's path. It SHALL also carry the last human
prompts and model texts of the old conversation. Those are newest kept within 16,000 characters, no tool input or output.

#### Scenario: A long conversation
- **WHEN** the old conversation holds more than 16,000 characters
- **THEN** the oldest are cut and no tool input or output appears

### Requirement: Notes stay current at a turn end
A turn end of an IN_PROGRESS task SHALL be refused once when `task_notes.md` is older than `orchestrator.agent.notesEvery`
(default `30m`, `0` never). Age counts from the later of the notes and the round's `task_context.md`.
The refusal asks for the notes rewritten within their cap. A turn end the refusal sent on is never refused again.

#### Scenario: Stale notes
- **WHEN** an IN_PROGRESS turn ends with notes 40 minutes old and `notesEvery` at 30m
- **THEN** it is refused once, asking for `task_notes.md` rewritten
- **WHEN** it ends again after that refusal
- **THEN** it is not refused
