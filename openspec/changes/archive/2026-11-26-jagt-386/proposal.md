## Why

A root session holding a root token from before a jagt restart gets 401 on `/mcp`, then runs OAuth discovery.
`/register` and `/.well-known/oauth-*` answered 403 "no Origin", which hides the cause.

## What Changes

- `/register` and `/.well-known/oauth-*` answer 404 with a body naming the cause: no OAuth, a stale root token, restart the session.
- Each logs one structured WARN.
