## Why

A refused compound push line was answered with a plain `git push`, which the host rejects after a rebase; the session concluded force pushing is forbidden.

## What Changes

- A refusal's push advice keeps the `--force-with-lease` form the refused line held, and says the push runs alone on its line.
- What the gate lets through is unchanged.

## Capabilities

### Modified Capabilities

- `git`: ToolGate's push advice keeps the lease.
