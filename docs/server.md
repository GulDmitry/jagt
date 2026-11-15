# jagt on a server

[← README](../README.md)

## Debug from a local CLI

A running jagt is read, never changed, through `read_record` and `read_transcript` on its MCP server and `GET /api/debug/…`
([debug-reads](../openspec/specs/debug-reads/spec.md#requirement-a-running-jagt-is-read-never-changed-through-its-own-door)).
State and reports: `list_tasks`, `GET /state`.

```sh
kubectl port-forward <pod> 8290:8290
curl -s -H "X-Jagt-Master: $(cat <token file>)" -H 'Content-Type: application/json' localhost:8290/mcp \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"read_record","arguments":{"record":"stops"}}}'
```

- `<token file>` is `master-token` beside `state.json`; point a CLI's MCP config at `http://localhost:8290/mcp` with that header.
- Under `auth.mode: password`, `/api/debug/…` needs the login session; `/mcp` needs the token.
- Records: `backend-log`, `master-log`, `stops`, `stops-reviewed`; under `orchestrator.logging: console` the two logs are what stdout carried.
