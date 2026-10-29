## Why

A task stopped as `WAIT` after its dependency stayed down was never asked again: the dependency came back, the session never learned, and the stop stood until a human lifted it.

## What Changes

- A `WAIT` stop keeps saying `wait-over` to the session after the last wait of `infraWait.backoff`; a wait again keeps the stop and the next wake due.
- A report of the session that is no wait ends the stop.

## Capabilities

### Modified Capabilities

- `infra-wait`: a session waiting out a dependency is asked again while its stop stands, and the stop ends when it goes on.
