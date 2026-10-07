# 0020 — jagt holds no token of its own

[← decisions](README.md) · rule: [`seams.md`](../rules/seams.md)

## Status

Accepted 2026-10-07. Keeps [0003](0003-tracker-and-code-host-are-read-through-the-agents-own-mcp.md); its
"reopen when" now lives here.

## Context

- 0003 left one concept open: jagt reading the tracker and the code host itself, once it could state what it
  promises before it holds a token.
- The path 0003 chose works: a headless one-shot of the agent CLI reads through the MCP servers of whoever runs
  jagt, on a light model, and every read is metered (`MeteredAssistant`).
- In daily use those reads were cheap enough that nobody asked for a faster or cheaper path.
- A token inside jagt puts a credential behind a board that listens on loopback without auth and can deploy.

## Decision

- jagt holds no tracker or code-host credential, and plans none.
- A new host or tracker is reached by an MCP server the install already holds, never by an adapter in jagt.

## Rejected

- Own REST adapters behind a stated token promise: a new security surface that buys nothing the MCP read lacks.

## Reopen when

- The metered `ROUTE`, `TICKET_READ` or `REVIEW_SWEEP` spend becomes a top cost of what jagt meters.
- A host or tracker that a team needs has no MCP server.
