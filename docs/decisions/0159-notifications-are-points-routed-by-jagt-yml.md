# 0159 — Notifications are points routed by jagt.yml

[← decisions](README.md) · rule: [`notification-routing`](../../openspec/specs/notification-routing/spec.md)
· supersedes [0132](0132-banners-are-chosen-per-event.md), [0142](0142-a-team-channel-is-a-configured-command.md)
· stands under [0130](0130-every-third-party-is-a-configured-seam.md), extends [0144](0144-what-was-told-once-is-one-ledger-written-by-the-reducer.md)

## Status

Accepted 2026-11-11 by the owner.

## Context

- 0132 chose banners per event on the desktop channel; 0142 added one command channel with its own event list.
- A second channel kind needs its own routes and texts; two lists keyed by the same event ids would drift.
- `MilestoneJob` diffed two statuses, so it missed `Checked.Passed` and forgot on restart.

## Decision

- **A point is a fact that stands**, declared once in `flow/role/Point` (an id and what makes it stand);
  a role adds `<role>-<kind>` for the kinds its `role.yml` `points` lists.
- **`jagt.yml` holds every channel, route and text**; code holds no default, `jagt.yml.dist` writes today's banners out.
- **Channel kinds** are `desktop`, `teams` and `command`; the command stays a script given the point as JSON on stdin.
- **`PointWatch` observes the persisted task** (`StateWritten`), not the call: any writer reaches the same facts.
- **Told once per (task, point, key)** in `ToldOnce.points`, written by `TaskReducer.told`; a restart tells silently.
- **A failed delivery is jagt's, not a card's**: one WARN, one board-head mark, retried until a post lands.
- **A secret is an environment variable name** (`webhookEnv`), never a value in `jagt.yml`.

## Rejected

- Advice around the calling methods: 30 writers reach the same facts and a refused call would still tell.
- Per-channel event lists: the drift this record ends.
- A Slack driver and the Teams MCP connector: a model would post in a person's name.

## Reopen when

- A point needs a channel that holds a long-lived connection, which a webhook or a command cannot reach.
