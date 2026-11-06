## Why

A model with no known price counted as 0 USD, so a total silently undercounted.

## What Changes

- A call whose cost is unknown is counted as unpriced, never as free.
- A total holding one reads as a lower bound (`≥`) in `stats`, `/master` and the stats median.
- One WARN per unpriced model per process names where prices live.

## Capabilities

### New Capabilities

- `unpriced-cost`: an unknown price is a lower bound, not zero.
