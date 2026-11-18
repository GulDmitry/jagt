## Why
No test pinned that a SIGTERM shutdown leaves the task in `state.json` with its session gone; the container suite's case was dropped without a port.

## What Changes
- A stand restart scenario sends SIGTERM through the engine seam and reads `state.json` before any revive.
