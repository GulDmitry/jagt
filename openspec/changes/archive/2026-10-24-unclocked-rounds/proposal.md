## Why

A red verification and a Master's not-ready round went back to the session with no clock and no ceiling: a session
that never got the build green, or a Master finding something new each round, looped for hours.

## What Changes

- The turn keeps when its step was relayed, rounds running.
- A failed verification, a failed probe and a not-ready plan or round go back at most three rounds running within
  24h since the last `lift`; the fourth stops the task for the human, its status kept.
- "Detection is deterministic" folds into the poll's requirement, keeping the page at its budget.

## Capabilities

### New Capabilities

### Modified Capabilities

- `unattended-work`: the loop stop covers rounds no thread count measures.
