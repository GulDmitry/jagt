## Why

Four things the human is asked to judge are readable only on the Mac: a failed check's output, what a trusted-git
change contains, jagt's log, and the worktrees nobody owns.

## What Changes

- A stop set by a failed check carries that check's output as its evidence, bounded and flagged where cut, shown
  where the stop is shown.
- The Diff tab shows what a trusted-git change contains, before the human trusts it.
- `tail` opens the tail of jagt's own log as a report.

## Impact

- Specs: new `board-judgement`.
- Code: `TaskStops`, `VerifyJob`, `GitStates`, `TrustedGitState`, `TaskDiffController`, `LogTail`, `LogCommand`.
