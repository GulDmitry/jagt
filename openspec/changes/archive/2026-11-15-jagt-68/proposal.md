## Why

Halting everything meant pausing every task by hand while jobs, intake and the Master carried on; a server owner needs one brake that outlives a restart.

## What Changes

- `stop` interrupts every working session with its CLI's interrupt, leaves it in tmux, pauses all jobs and intake and reads the Master as `judge`; one confirm.
- The stop is a flag beside `state.json`: a restart starts stopped, and only the human's `unstop` or the mark on the board's head ends it.
- The mark reuses the `--you` ring of `.chip.stalled` and replaces pausing each task by hand; no new colour meaning.

## Impact

- `board-verbs`: gains "A board-wide stop halts all work until your lift" and "The stop is one mark on the board's head".
