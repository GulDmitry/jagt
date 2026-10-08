## Why

The `git` spec named a `pushBranch` that no longer exists and hid that the resume rebase pushes under
`--force-with-lease`; `task-flow` promised a foreign push never leaves the machine, which a skipped hook breaks
([0022](../../../../docs/decisions/0022-the-resume-rebase-is-the-one-rewrite-and-the-guard-is-a-guardrail.md)).

## What Changes

- The `git` spec names the classes that push and the one rewrite.
- `task-flow` promises a refusal of a plain foreign push, a guardrail rather than a boundary.

## Capabilities

### Modified Capabilities

- `git`: what pushes, and the one rewrite, by name.
- `task-flow`: the push guard's promise matches what it does.
