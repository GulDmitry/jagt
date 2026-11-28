## Why

A session could force-add a file jagt writes into its worktree and push it with the task's work.
Nothing named such a file at the push, and no rule tied a writer to `Artifact`.

Kind: hook script, rule test
Holds: today's architecture; `Artifact` is the one list, the hook reads it when written
Written: pending
Uncaught: no test pushed a force-added artifact through the guard
Unseen: the guard only judged destination branches
Prevents: WorktreeHooksTest refuses plan.md, acceptance-report.md and acceptance/; WorktreeWritersFitness fails a writer outside the list

## What Changes

- The push guard refuses a commit carrying an `Artifact` at the worktree root, naming the file.
- A rule test fails a worktree writer by a literal name that `Artifact` does not list.

## Capabilities

### Modified Capabilities

- `git`: what the push guard refuses.
