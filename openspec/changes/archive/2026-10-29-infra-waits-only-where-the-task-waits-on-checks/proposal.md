## Why

A deployed or reverted task whose open request went red on a runner armed retries and stopped as `INFRA`, though nothing waited on its checks.

## What Changes

- The infra retry and the `INFRA` stop apply only at statuses whose next step waits on the checks, the set `FlowRules` already holds for a red read.

## Capabilities

### Modified Capabilities

- `infra-wait`: the retry and the stop follow the statuses that wait on the checks.
