## Why

A task in `act` was stopped on its fifth ship in a day while its open threads fell every round (3, 2, 1): a bot-review
back-and-forth that was finishing, and the human lifted the stop.

## What Changes

- A ship counts toward `loopBreaker.ships` only when the round it answers did not bring the open threads below the
  lowest of the earlier rounds since the last `lift`; the first round sets that minimum and does not count as a low.
- A ship with no round before it counts.

## Capabilities

### New Capabilities

### Modified Capabilities

- `unattended-work`: the ships trigger counts ships without a new low.
