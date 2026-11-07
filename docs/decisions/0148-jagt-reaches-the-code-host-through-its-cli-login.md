# 0148 — jagt reaches the code host through its CLI login

[← decisions](README.md) · rule: [`seams`](../rules/seams.md)

## Status

Accepted 2026-11-07. Supersedes in part [0003](0003-tracker-and-code-host-are-read-through-the-agents-own-mcp.md)
and [0020](0020-jagt-holds-no-token-of-its-own.md) for code-host reads and writes.

## Context

- 0003 said jagt never calls a code host itself; 0020 said it holds no token.
- Since 0105, 0109 and 0133, jagt reads pipelines, failed jobs and request notes, and writes a re-run and a request close.
- 0130 already superseded 0003 in part: the code host is a configured seam.
- The drivers (`gh`, `glab`) run the host's own CLI, acting with the login of whoever runs jagt.

## Decision

- jagt reaches the code host only through the `CodeHost` port; each driver runs the host's CLI under the human's own login.
- jagt still stores no token: 0020 holds for storage.
- The tracker stays read through the agent's MCP: 0003 holds for the tracker.
- The writes are exactly those `AGENTS.md` allows: the infra re-run bounded by `infraWait`, and the request close of
  `remove-repo`.

## Rejected

- REST adapters with a token in jagt: 0020's reason, a credential jagt would then hold.

## Reopens when

- A host has no CLI.
- A write beyond those named is wanted.
