## Why

The cold-prompt gate read the newest log in the worktree's directory. The fresh session a cold prompt went to has no
log yet, so its first prompt met the old session's log, read cold, and was relayed again, every ~2.5 s (seen 2026-10-28).

## What Changes

- The turn end is read from the log the prompting session names, not the newest in the directory.

## Capabilities

### Modified Capabilities

- `cold-prompts`: a session whose own log holds no turn end is never cold.
