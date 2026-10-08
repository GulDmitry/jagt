## Why

`deployBranch: refs/heads/main` beside `baseBranch: main` passed the deploy guard and the startup check, and a deploy
then pushed onto the base branch.

## What Changes

- `deployBranch` and `baseBranch` are compared as the branch they name: `refs/heads/`, `refs/remotes/origin/`,
  `origin/`, a leading `+` and whitespace drop away.

## Capabilities

### Modified Capabilities

- `git`: the deploy guard names the pair it refuses, however spelled.
