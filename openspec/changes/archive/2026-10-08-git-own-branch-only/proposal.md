## Why

A task id only had to be a valid branch name. `initialize_task{taskId:"main",branchStrategy:"resume"}`, or resuming a
release request from `dev` into `main`, made jagt rebase a shared branch and push it under a lease, its own pre-push
hook allowing it.

## What Changes

- A task named after any project's base or deploy branch, or after its own base override, is refused before anything
  is cut, however spelled and in any case.

## Capabilities

### Modified Capabilities

- `git`: a task's branch is its own, never a shared one.
