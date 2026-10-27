## Why

The cold-prompt gate read idle from a mark in jagt's memory. A restart wipes it, and an unlogged event cleared it:
a turn ended 13:16Z, a prompt at 18:06Z passed as warm (seen 2026-10-27, 4h50 idle).

## What Changes

- Idle is read from the session's own transcript: the last turn-end entry, ignoring a later prompt or compaction.
- The in-memory mark in `SessionProbe` is removed.

## Capabilities

### Modified Capabilities

- `cold-prompts`: the cold-prompt requirement counts idle from the transcript.
