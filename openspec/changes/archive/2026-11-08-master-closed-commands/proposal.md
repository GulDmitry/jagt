## Why

`run_command` lets the Master run every command, `do` and `resume` open a task and `act` lifts the Master's own brake: a task opens
from a deterministic source alone, and a brake on the Master is not its to lift.

## What Changes

- A `GlobalCommand` may declare `whyNotTheMaster`; `MasterPresses.run` refuses it with that reason, whatever `mine` says.
- `do`, `resume` and `act` declare it.

## Impact

- Spec: `master`.
- `GlobalCommand`, `DoCommand`, `ResumeCommand`, `ActCommand`, `MasterPresses`, `MasterVerbsTest`.
