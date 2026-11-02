## Why

A recorded mark that a repository was pushed alone was set on relay, wiped by any round landing IN_PROGRESS and never
ended by a config change.

## What Changes

- The mark is gone: the deploy without a request is read from the configuration and the worktrees when offered.
- A sweep never asks a repository with no code host for a request.

## Capabilities

### Modified Capabilities

- `git`: what ship leaves a repository with no code host.
