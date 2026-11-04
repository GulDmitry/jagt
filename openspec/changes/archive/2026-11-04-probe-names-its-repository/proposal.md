## Why

A plan of a several-repository task cannot probe a repository but the primary one.

## What Changes

- A command probe names the task repository it runs in: `command [repo=<project>] [exit=<n>] <argv…>`.
- A probe naming a repository the task lacks is refused at PLAN_PENDING.
- A probe naming none runs in the primary worktree, as before.

## Capabilities

### Modified Capabilities

- `acceptance-probes`: a plan declares its probes in one form; jagt runs the probes at VERIFYING.
