## Why

The stand's absolute start budget failed runs on a loaded machine; it holds only on the idle reference machine.

## What Changes

- The absolute budgets are judged only with `-Pbudgets=true`; every other run is judged by the trend alone.

## Capabilities

### New Capabilities

### Modified Capabilities

- `stand-engines`: a run without `-Pbudgets=true` is a trend-only run.
