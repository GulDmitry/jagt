## Why

A stop ended on a green read left the task's checks, approval and unread flag at what the last sweep stored before the outage.

## What Changes

- The read that ends a stop on a read stores the checks and approval it listed, through the one writer of those facts.
- It stores nothing where the read listed no checks, and moves nothing else.

## Capabilities

### Modified Capabilities

- `infra-wait`: the probe records what it read.
