# 0003 — The tracker and the code host are read through the agent's own MCP

[← decisions](README.md) · rule: [`seams.md`](../rules/seams.md)

## Status

Accepted 2026-08-24 (`7e9e35a`), recorded retroactively 2026-10-02.

## Context

- 2026-08-13: `99e7564` added `port/CodeHost` (GitLab v4) so the review sweep read over REST when
  `orchestrator.code-host.*` was set, falling back to the paid headless read; `52a8c60` opened the merge request
  over REST too.
- 2026-08-17: `7bd2270` added GitHub behind `CodeHost` and a `port/Tracker` (Jira) for the one ticket a launch
  needs.
- The seam was never the owner's decision: a session wrote it into its own roadmap and shipped it the same day.
  Every session since found the config keys, tried them and reported the app as broken.
- It could not be finished: reading a host by API needs a credential inside jagt, and jagt holds none. The board
  listens on loopback without auth and can already deploy; with a token behind it, whoever reaches the port
  acts as the human on the host.

## Decision

- jagt never calls a tracker or a code host itself. A headless one-shot of the agent CLI reads them through the
  MCP servers of whoever runs jagt, and `ship` hands commit, push and one request per repository to the session.
- `ReviewReader` and `TicketReader` keep that one path each, with `paidRead`, the `failure` field and the MCP
  probe, so a live request is never reported as missing.
- `7e9e35a` removed `CodeHost`, `Tracker`, `JsonHttp`, the GitLab, GitHub and Jira adapters, `CodeHostProperties`,
  `TrackerProperties`, `OutsideReadsCheck` and the `tracker` / `code-host` blocks of `application.yml`: 55 files,
  2983 lines deleted.

## Rejected

- REST adapters behind `CodeHost` and `Tracker`, shipped 2026-08-13 to 2026-08-24: they need a credential jagt
  must not hold, and dead keys invite every session to wire them.

## Reopen when

- jagt can state what it promises before it holds a token: where it is read from, what may act with it, and
  what the board needs first ([`TODO.md`](../../TODO.md)).
