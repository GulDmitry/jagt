## Why

Time behaviour had no asserted budget; start-to-ready and `/state` latency are pinned with headroom over the measured values.

## What Changes

- `containerTest` asserts the app up within 15 s of its process start and `/state` p95 within 100 ms over 50 sequential calls.

## Capabilities

### Modified Capabilities

- `engineering`: the pyramid requirement names the budgets; the time-behaviour gap is closed.
