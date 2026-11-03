## Why

A request opening and a deploy landing told nobody: `YourMoveJob` pings only the human's move, and neither status is
one. Which banners an owner wants is theirs to choose, event by event.

## What Changes

- `orchestrator.notifications.desktop` lists the events that raise a desktop banner; unset keeps today's banners.
- Two new events: `request-opened` (on by default) and `deployed` (off by default).
- `MilestoneJob` raises them when a task's status turns into `CI_POLLING` or `DEPLOYED`; a task first seen is silent.

## Capabilities

### Modified Capabilities

- `attention`: desktop banners are chosen per event; a request opening and a deploy landing are events.
