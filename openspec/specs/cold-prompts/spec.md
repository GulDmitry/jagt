# cold-prompts Specification

## Purpose
A prompt typed into a session idle past its cache costs the whole context again.
jagt sends it to a fresh session and keeps that session's notes current.

## Requirements

### Requirement: A cold prompt starts fresh
A session may stand idle for `orchestrator.agent.cacheLifetime` (default `1h`, `0` never). A prompt typed after that
SHALL be written under the brief in `task_context.md` and start a fresh session (`AgentSessions.relayUnderCold`).
Only then SHALL it be blocked, with one line saying where it went.

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

### Requirement: A session's idle time is its last sign
Idle SHALL count from `Session.idleSince`, the later of the session's halt and last sign (a hook, a tool use, a
session-log line; never a repaint), in every phase, an open turn included: a turn alive signs within the lifetime.
It never counts where the session has signed nothing. `SessionPhase.cold` is the one predicate every caller reads.
The record is persisted, so a restart of jagt never reads a cold session as warm.
The first read after the start stamps its start.

#### Scenario: jagt restarted while the session idled
- **WHEN** jagt restarted while the session idled past the lifetime
- **THEN** a prompt still goes to a fresh session

#### Scenario: An open turn silent past the lifetime
- **WHEN** a session whose turn is open has signed nothing for the lifetime and a prompt or relay comes
- **THEN** it goes to a fresh session

#### Scenario: The fresh session's first prompt is never relayed again
- **WHEN** a prompt arrives within the lifetime of the fresh session's first read, as its first one does
- **THEN** it reaches the model
- **WHEN** a prompt arrives before the fresh session's first hook
- **THEN** it passes: a fresh session starts the record over, so the old session's idle stamp is gone
- **WHEN** no hook has landed within the stale limit of the first read after the start, however old the previous transcript
- **THEN** the fresh session is not QUIET, and no stopped banner is raised for it

### Requirement: jagt says nothing into a cold session
A line jagt says into a session (`AgentSessions.say`) past the cache lifetime SHALL be written under the brief and
start a fresh session (`relayUnderCold`). The restart ask SHALL skip a cold session and log it: its report predates
the lifetime, so nothing a restart lost is newer.

#### Scenario: A line to a cold session
- **WHEN** jagt says a line to a session cold past the lifetime
- **THEN** the line is in `task_context.md` and a fresh session reads it; nothing is typed into the old one

#### Scenario: jagt restarts beside a cold session
- **WHEN** jagt starts and a running session holding an open turn is cold
- **THEN** it is not asked to repeat its report, and the skip is logged

### Requirement: A fresh start carries what the old session knew
Beneath the instructions in `task_context.md`, a fresh start SHALL carry, gathered without a model: the last report or
open question, the commits and changed files since the base (40 lines each), and the old transcript's path.
It SHALL also carry the last human prompts and model texts of the old conversation. Those are newest kept within
16,000 characters, no tool input or output.
A fresh start replacing a session with no transcript SHALL carry the report and the git facts, and no conversation
or transcript path.

#### Scenario: A long conversation
- **WHEN** the old conversation holds more than 16,000 characters
- **THEN** the oldest are cut and no tool input or output appears

#### Scenario: A session with no transcript
- **WHEN** a fresh start replaces a session with no transcript
- **THEN** `task_context.md` holds the last report and the commits and files since the base,
  and no conversation or transcript path

#### Scenario: A round replaces a session the human talked to
- **WHEN** a round's fresh start replaces a session whose transcript holds the human's last words
- **THEN** `task_context.md` holds the round's instructions first, then the human's last words

### Requirement: Notes stay current at a turn end
A turn end of an IN_PROGRESS task SHALL be refused once when `task_notes.md` is older than `orchestrator.agent.notesEvery`
(default `30m`, `0` never). Age counts from the later of the notes and the round's `task_context.md`.
The refusal asks for the notes rewritten within their cap. A turn end the refusal sent on is never refused again.

#### Scenario: Stale notes
- **WHEN** an IN_PROGRESS turn ends with notes 40 minutes old and `notesEvery` at 30m
- **THEN** it is refused once, asking for `task_notes.md` rewritten
- **WHEN** it ends again after that refusal
- **THEN** it is not refused

### Requirement: A fresh start waits for the human
A fresh start SHALL NOT close a window holding input the human typed and has not sent.
Nor SHALL it while the turn their own prompt opened is still open. The instructions stay on disk and the fresh start waits, logging
`fresh start waits`. It proceeds when neither holds on two consecutive reads, two seconds apart, the window is gone or the task is removed.
A read that fails keeps the wait and is read again.

#### Scenario: A round arrives while the human types
- **WHEN** a new round arrives while the window holds the human's unsent input
- **THEN** the fresh start waits until it is sent and the turn ends

#### Scenario: A round arrives during the human's exchange
- **WHEN** a new round arrives while the turn their prompt opened is open
- **THEN** the fresh start waits until that turn ends

#### Scenario: The window dies while the fresh start waits
- **WHEN** the window is gone or the task removed during the wait
- **THEN** the wait ends and a fresh session starts, or nothing where the task is gone

#### Scenario: A read that fails keeps the wait
- **WHEN** the window cannot be read while the fresh start waits, and the human is gone on the reads after
- **THEN** the wait stands through the failure and the fresh session starts
