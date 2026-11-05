## MODIFIED Requirements

### Requirement: A session's idle time is its last sign
Idle SHALL count from `Session.idleSince`, the later of the session's halt and last sign (a hook, a tool use, a
session-log line; never a repaint), in every phase, an open turn included: a turn alive signs within the lifetime.
It never counts where the session has signed nothing. `SessionPhase.cold` is the one predicate every caller reads.
The record is persisted, so a restart of jagt never reads a cold session as warm.
Starting a fresh session starts the record over, so nothing the old session signed speaks for the new one.
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
- **THEN** it passes, the old session's idle stamp being gone
- **WHEN** no hook has landed within the stale limit of the first read after the start, however old the previous transcript
- **THEN** the fresh session is not QUIET, and no stopped banner is raised for it

## ADDED Requirements

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
