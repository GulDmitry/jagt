## Why

Claude rewrites `~/.claude.json` from its own copy, outside any lock jagt can take, so a worktree trusted at
provisioning can lose its flag before its session starts; that session then sits on the trust dialog.

## What Changes

- Right before a session's launch line is sent, the runtime checks the flag and writes a lost one again, up to three
  times, then launches.

## Capabilities

### New Capabilities

### Modified Capabilities

- `runtime`: the worktree is trusted at launch, not only at provisioning.
