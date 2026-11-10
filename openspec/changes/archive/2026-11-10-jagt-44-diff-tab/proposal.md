## Why

Reading a task's change before `ship` opens the IDE on the desk; a board in a browser, or away from the desk, cannot.

## What Changes

- The open task gets a Diff tab beside Artifacts: its diff per repository against the base the IDE diff reads.
- The read is bounded: past a cap the diff is cut and the tab says so.
- The review hint and the deploy and revert conflict hints stop naming the IDE.
- The IDE verb stays.

## Impact

- Specs: `board-workspace`.
- Code: `DiffBases`, `TaskDiff`, `TaskDiffController`, `DiffCheckouts`, `IdeDiff`, `Move`, the board's tabs.
