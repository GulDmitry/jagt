## Why

The classes the fixes return to every month stayed unnamed until a review happened to meet them.

## What Changes

- `hotSpots` counts, per main class, the commits of the last 30 days carrying a `Defect-family:` trailer other than `L`, and prints the top ten.
- It fails above `hotSpots.max` and below it, so the ceiling only falls; each release review runs it.

## Capabilities

### New Capabilities

### Modified Capabilities

- `engineering`: a class the fixes keep returning to is named and ceilinged.
