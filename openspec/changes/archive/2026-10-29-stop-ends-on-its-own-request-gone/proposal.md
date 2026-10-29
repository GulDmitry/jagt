## Why

A request closed on the host, a task with no request left, and a gone request the stop was never counted on all decided a stop wrongly.

## What Changes

- A request the host says is closed unmerged counts as gone.
- A task with no request linked ends a stop that ends on a read.
- A gone request ends a stop only where the stop was counted on it, or on none.

## Capabilities

### Modified Capabilities

- `infra-wait`: the request-is-gone scenario is widened, the unlinked one added.
