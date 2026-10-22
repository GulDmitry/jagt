# 0056 — A report to jagt changes nothing

[← decisions](README.md) · spec: [`protocol`](../../openspec/specs/protocol/spec.md)

## Status

Accepted 2026-10-22.

## Context

- A client may gate calls that change things: a read-only or planning mode lets a tool through only when it says it changes nothing.
- jagt listed no annotations, so every tool counted as changing something and a gated session could not call any.
- A session that cannot report its status or ask its question blocks unseen.

## Decision

- Every tool declares an `Effect` in `McpToolRegistry.tool`; `tools/list` sends `annotations.readOnlyHint` from it.
- `REPORTS`: `update_agent_status`, `notify_user`, `list_tasks`, `list_projects`, `open_in_ide`. Every other tool is `CHANGES`.
- A report to jagt touches nothing a session works on, so it is safe in any client mode.
- This holds for every client mode, not one launch path: jagt stops launching sessions in the CLI's plan mode (decision 0059).

## Evidence

- Probe MCP server, 2026-10-22: Claude Code's plan mode ran a tool listed with `readOnlyHint: true`.
- It refused the unannotated one with `Cannot call mcp__probe__rw_ping while in plan mode.` despite `--allowedTools mcp__probe`.

## Rejected

- Allow rules for the tools: the gated mode ignores them for an unannotated tool.

## Reopen when

- A client reads `readOnlyHint` as licence for more than its permission mode.
- A `REPORTS` tool starts writing the worktree or git.
