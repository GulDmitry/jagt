## Why

`ToolGate` does not decode `$'…'` or `$"…"`: `git $'\x70ush' origin main` was never read as a push.

## What Changes

- A git line holding `$'` or `$"` is refused.

## Capabilities

### Modified Capabilities

- `git`: a quoting the gate does not read refuses a git line.
