## Why

A fact read before a move could land after it and overwrite the newer state; only local counters guarded it, each in one caller.

## What Changes

- Every move whose edge writes what a read saw carries the generation it was read at: session start, head tip or requests linked.
- The reducer drops a read the task has moved past, state unchanged, and logs `read dropped stale` with its kind.
- The watchdog's session-start guard moves into the reducer; the fitness `fact-generations` holds every fact move to a generation.
- The model check takes a late read in every state with an open request and checks S6: no read moves a newer state.
- Two task-flow requirements drop the blank line before each scenario to stay within the page budget.

## Capabilities

### Modified Capabilities

- `task-flow`: a read lands only on the generation it was taken at.
