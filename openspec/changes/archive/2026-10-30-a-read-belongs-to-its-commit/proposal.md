## Why

A red read of the commit before kept showing, and kept its infra retry armed, after the session pushed a fix.

## What Changes

- The checks and approval a read stored carry the pushed commit they were read on; on any other commit than the request's current one they read as unknown.
- A push disarms the retry armed for the commit before.
- A stored read with no commit reads as unknown once a head is known.

## Capabilities

### Modified Capabilities

- `infra-wait`: a stored read belongs to its commit.
