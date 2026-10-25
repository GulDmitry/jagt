## Why

Spend is counted per call kind and per open task, but nothing says what one task that reached DEPLOYED cost.

## What Changes

- Each `finished.jsonl` row holds `costUsd`, the task's agent session plus jagt's own reads of it; absent where the session's spend was never read.
- The `stats` report ends with the median `costUsd` of deployed tasks, adding no board mark.

## Capabilities

### Modified Capabilities

- `usage-limits`: a finished task keeps its cost, and `stats` shows its median.
