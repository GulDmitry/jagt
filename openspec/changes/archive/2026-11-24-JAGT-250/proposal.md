## Why

A multi-repository task was refused whole because one repository has no `deployBranch`; a project whose request goes straight to main has none by design.

## What Changes

- `deploy` lands the repositories that name a `deployBranch` and names each skipped one and why in its outcome.
- A task with no deployable repository, or a `deployBranch` equal to its base branch, is refused as before.

## Capabilities

### New Capabilities

### Modified Capabilities

- `git`: a repository without a `deployBranch` is skipped, never refuses the deploy.
