# 0232 — Every model role's log is on the board and in no tmux window

[← decisions](README.md) · rule: [`board`](../../openspec/specs/board/spec.md) · amends [0017](0017-the-master-is-seen-in-one-feed-window.md) ·
on [0162](0162-a-check-holds-for-all-code.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-23.

## Context

- 0017 gave the Master one standing tmux window following `jagt-master.log`; acceptance and triage had no feed.
- The board is the one surface (AGENTS.md "Interfaces"); a window the board cannot show is a second surface.
- Each role is a module (`RoleModules`): what it logs is the module's classes.

## Decision

- **One feed mechanism for every model role**: a logback appender `ROLE_<id>` of one kind (`RecentLines`) on the role module's classes,
  keeping the last 2000 numbered lines in memory; a role added later declares its appender and a `ModelRole` bean, nothing else
  (`RoleFeedWiringTest`).
- **The board reads it, never polls**: `/api/roles/<id>/log?after=<n>`; each new line sends the `role-log` event on the push stream.
- **The public surface rises by one**: `/api/roles/<id>/log` (`public-surface`, 0176).
- **A chip per running model role** in the head, none for a disabled role or one a human holds; pressing it opens the log in the pane, again hides it.
- **`MasterFeedWindow` and the `master` window are removed**; `jagt-master.log` stays the file `read_record` and the roll read.
- **What the legend row replaces**: the tmux `master` window, the only place the Master's steps showed.

## Rejected

- A tmux window per role: a second surface, and one more window for every role added.
- A file per role tailed by the backend: no file exists under console logging, the memory feed works in both.
- Polling the log from the page: the board never polls.

## Reopen when

- A role's log outgrows 2000 lines between two looks, or a step the human needed is missing from it.
