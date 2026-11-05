## Why

A line jagt sent after a restart wrote two idle sessions' whole contexts cold: `say` had no cold check, and a session
whose last sign was "working" never read cold.

## What Changes

- A session is cold when its last sign is past the cache lifetime, in every phase; one predicate, `SessionPhase.cold`.
- A line said to a cold session goes to a fresh one, as a relay does.
- The restart ask skips a cold session.

## Capabilities

### Modified Capabilities

- `cold-prompts`: idle counts from the last sign while the turn is open too; `say` and the restart ask honour it.
