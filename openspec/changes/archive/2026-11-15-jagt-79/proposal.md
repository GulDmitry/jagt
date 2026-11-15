## Why

A link in a notification or banner from a server names `localhost`, which points nowhere for the person reading it.

## What Changes

- An optional `server.publicUrl` in `jagt.yml`; every link jagt sends to a human starts with it, `http://localhost:<port>` when blank.
- The sessions' own URLs (mcp, hook, gate) stay loopback.

## Impact

- `notification-routing`
