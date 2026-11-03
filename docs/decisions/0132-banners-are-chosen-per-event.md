# 0132 — Banners are chosen per event

[← decisions](README.md) · rule: [`attention`](../../openspec/specs/attention/spec.md) · amends [0067](0067-pings-follow-the-owner-flip.md)

## Status

Accepted 2026-11-03.

## Context

0067 made `YourMoveJob` the one status ping. A request opening (`CI_POLLING`) and a deploy landing (`DEPLOYED`) are not
the human's move, so both were silent; the owner wants them, and wants each banner chosen, not all or none.

## Decision

- Every notification carries one event id (`Notification.Event`); the desktop channel raises a banner for the events
  `notifications.desktop` lists, decided in `DesktopNotifier.takes` and in no caller.
- Unset, the list is today's banners plus `request-opened`; `deployed` is off.
- `MilestoneJob` raises `request-opened` and `deployed` when a status turns into `CI_POLLING` or `DEPLOYED`:
  a task first seen, a restart included, is silent; the map holds only present tasks.
- 0067 stands for the human's move: `YourMoveJob` still pings it, and `AgentStatusReports` still pings nothing.
- "What is merely coming is not news" holds by default; an owner opts in per event.

## Rejected

- A ping at each place that writes `CI_POLLING` or `DEPLOYED`: 0067's reason, a writer added later is silent.
- A per-caller check of the config: the filter would live in a dozen places.
- `merged` as an event: `done` is already the human's move, so `your-move` covers it.

## Reopen when

- A second channel needs its own event list: the list moves from the desktop key to the channel.
- A task re-enters `CI_POLLING` often enough (a re-ship after a red run) that `request-opened` is noise.
