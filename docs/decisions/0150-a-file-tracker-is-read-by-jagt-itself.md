# 0150 — A file tracker is read by jagt itself

[← decisions](README.md) · amends [0003](0003-tracker-and-code-host-are-read-through-the-agents-own-mcp.md)

## Status

Accepted 2026-11-08.

## Context

- 0003: a tracker is read by a model through the MCP of whoever runs jagt, because reading one by API needs a credential
  jagt must not hold.
- A Markdown checklist has no server and no credential: reading it costs no model and no token.

## Decision

- `orchestrator.tracker.workflow: file` with `orchestrator.tracker.file` (no default; startup refuses `file` without it).
- `FileWorkflow` (`TrackerWorkflow`) and `FileTrackerAssistant` (`TrackerAssistant`, primary) live in `adapter/tracker`.
- An item is a top-level `- [ ]` / `- [x]` line carrying a key; `[ ]` reads as `open`, `[x]` as `done`; its body runs to the
  next top-level list line or heading.
- A key the file holds is answered from the file at no cost; any other reference goes to the assistant chosen without it.
- An unreadable file is an unavailable answer with a WARN naming its cause, never an empty list.
- Start and close compare the status with `startStatus` and `doneStatus`, as for `jira`; no assignee is asked.
- The item's link is `file://<absolute path>#<KEY>`; `TicketRead` accepts a `file` link beside `http(s)`.

## Rejected

- A model reading the file: it costs tokens for a fact a parser answers.
- Writing the file back (checking an item off): a tracker jagt writes needs its own decision.

## Reopens when

- A file tracker needs writes, or a second file format is asked for.
