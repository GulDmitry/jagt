## Why

jagt's own spend per kind lived in memory alone, so no change to a call's cost could be checked across days
([0038](../../../docs/decisions/0038-a-panels-first-role-writes-the-round.md)).

## What Changes

- Every metered call adds to `usage-by-day.jsonl`, folded into months and years as it ages.

## Capabilities

### Modified Capabilities

- `usage-limits`: jagt's own spend is kept per period and kind.
