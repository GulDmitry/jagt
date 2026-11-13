## Why

Notifications are a model of their own: code holds points, `jagt.yml` holds every channel, route and text.

## What Changes

- A point is routed to the channels `notifications.points` lists, each with its own text, told once per key.
- A role declares its points in `role.yml`.
- `notifications.desktop`, `notifications.channel` and `MilestoneJob` give way to channels and routes.

## Capabilities

### Modified Capabilities

- `attention`: the three notification requirements become two.
