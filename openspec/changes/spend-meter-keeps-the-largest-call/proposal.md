## Why

The spend ceiling's default cannot be set from data while the meter keeps only sums.

## What Changes

- Each `usage-by-day.jsonl` row also holds `maxCallCostUsd`, the dearest single call of its period and kind.

## Capabilities

### Modified Capabilities

- `usage-limits`: the per-period spend line keeps the largest single call.
