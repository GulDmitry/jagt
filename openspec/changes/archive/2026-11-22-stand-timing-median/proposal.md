## Why

The action compares only with the previous entry, so one fast run made the next normal run a "regression".

## What Changes

- The stand job fails when a series passes 200 % of the median of its last ten history entries.
- The benchmark action only stores the trend.

## Capabilities

### New Capabilities

### Modified Capabilities

- `stand-engines`: the trend is judged against a median.
