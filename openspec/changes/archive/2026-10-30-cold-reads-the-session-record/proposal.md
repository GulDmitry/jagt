## Why

Silence, a cold prompt and a relay read three clocks: the session record, a transcript's turn end and a transcript's last entry.

## What Changes

- A prompt and a relay call a session cold from one stamp, `Session.idleSince`, never while its turn is open.
- A session that has signed nothing is not cold.

## Capabilities

### Modified Capabilities

- `cold-prompts`: idle counts from the session record.
- `runtime`: a relay past the cache lifetime reads the same stamp.
