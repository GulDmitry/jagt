## Why

Claude Code compacts a session left idle before its cache expires. The compaction starts the session again, which
jagt reads as work: the turn's end is forgotten, so a prompt typed 75 minutes after it went into the old conversation
instead of a fresh session (seen 2026-10-27, 79k tokens written to the cache).

## What Changes

- A task session's settings carry `idleCompaction: false`: an idle session stays as its turn left it.

## Capabilities

### Modified Capabilities

- `runtime`: the compaction requirement names the setting.
