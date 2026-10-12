## Why

A task that failed half-way left a worktree and branch nothing registered, and a retired task whose project left
`jagt.yml` left its diff checkouts and the agent's record of the worktree.

## What Changes

- A resume step failing after `git worktree add` removes that worktree, and the branch where the step cut it.
- A registration failing after the cut unwinds every worktree, as a failed cut does.
- `done` on a repository whose project left `jagt.yml` still drops the agent's record and the diff checkouts.

## Capabilities

### Modified Capabilities

- `git`: creation unwinds every failure; retirement without a project still clears what needs none.
