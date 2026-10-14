## Why

In `act` the Master kept pressing however its moves ended: a run of silent sessions, deploy conflicts and reverts
went on writing the shared branch with nobody watching.

## What Changes

- Three tasks in a row the Master moved ending silent, in a deploy conflict or reverted stop `act`; it judges.
- A deploy of such a task resets the count.
- The stop outlives a restart; the header shows `act stopped` in the amber ring, and pressing it gives `act` back.
- The feed window requirement moves to `unattended-work`, unchanged but for the stop it now shows.

## Capabilities

### Modified Capabilities

- `master`: `act` stops itself after three bad endings in a row.
- `board`: a stopped `act` gets its own mark.
- `unattended-work`: the Master's feed window, and the stop in it.
