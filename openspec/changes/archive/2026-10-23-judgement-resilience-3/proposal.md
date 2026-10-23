## Why

A Master read runs in its worktree and may call `git log` or `Grep` with no path, so no call's input need name the
worktree: a right verdict failed on that check. The gate is told each call's working directory.

## What Changes

- `masterEval` asserts the read gate heard a call made in the run's worktree.

## Capabilities

### New Capabilities

### Modified Capabilities

- `judgement-resilience`: a call is the run's by its working directory, not by a path in its input.
