## Why

With auto memory off, a gap a worker hit lands in the project's agent file, reviewed in the request; unbounded and
unexplained, that file rots the way auto memory did.

## What Changes

- A hand-back is refused when the task added more than 3 lines to the project's agent files, or fewer
  `agent-file:` lines in `task_notes.md` than it added.

## Capabilities

### Modified Capabilities

- `runtime`: a hand-back names every line it added to the project's agent file.
