## Why

Under `auth: password`, `/mcp` and `/api/agent/` were outside the login and named a task by its worktree header alone, so any peer reaching a non-loopback bind could act as a session.

## What Changes

- A peer off loopback reaches `/mcp` and `/api/agent/` only with the Master token; a loopback peer is served as before.

## Impact

- `board`: "Loopback, or behind a login" gains the rule in its prose and its "A session" scenario.
