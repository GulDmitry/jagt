## Why

add-repo rewrote every worktree's briefing by asking for a fresh name, so a worktree whose repository ships its own
memory file, where jagt had briefed in the local one, refused its own earlier briefing and the attach failed.

## What Changes

- A worktree jagt already briefed is briefed again in the file it wrote (`AgentRuntime.briefedFile`).

## Capabilities

### New Capabilities

### Modified Capabilities

- `git`: add-repo rebriefs a held worktree in place.
