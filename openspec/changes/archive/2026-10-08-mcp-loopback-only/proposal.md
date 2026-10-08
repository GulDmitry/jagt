## Why

Loopback is no wall against a browser: a page elsewhere could post a verb, or rebind its own name to 127.0.0.1,
and deploy without a preflight.

## What Changes

- A request naming another Host, or carrying another Origin, is refused 403 before any controller.
- `/mcp` takes `application/json` alone, so a form cannot send it.

## Capabilities

### Modified Capabilities

- `board`: loopback is enforced per request, not only by the bind address.
