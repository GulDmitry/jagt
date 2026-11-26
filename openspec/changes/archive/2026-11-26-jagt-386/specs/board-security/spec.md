## ADDED Requirements

### Requirement: jagt has no OAuth
`/register` and `/.well-known/oauth-*` SHALL answer 404, not 403, with a body naming the cause.
The body says jagt has no OAuth, a stale root token after a restart leads a client there, and the session restarts.
Each such request SHALL log one structured WARN.
#### Scenario: A root session holds a token from before jagt restarted
- **WHEN** a client refused on `/mcp` with a stale root token posts `/register` or reads `/.well-known/oauth-authorization-server`
- **THEN** it gets 404 naming the stale token and the restart, never 403 "no Origin"
