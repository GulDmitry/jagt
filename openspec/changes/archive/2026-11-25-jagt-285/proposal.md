## Why

A post the full delivery backlog rejected was dropped with one WARN, though a point is retried until it lands.

## What Changes

- A rejected post stays pending, marks the board's head and is retried on the post's own backoff.

## Capabilities

### New Capabilities

### Modified Capabilities

- `notification-routing`: a post the delivery backlog rejects is retried like one a channel refuses.
