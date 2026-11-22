## Why

A chart's edge that no test drives can break unseen: the macrostep returned the configuration and effects, not the transitions it took.

## What Changes

- A macrostep reports the transitions it took, in order.
- `TaskChartProperties` explores the task chart and fails on a drawn edge no move fires.

## Impact

- `openspec/specs/statechart/spec.md`
