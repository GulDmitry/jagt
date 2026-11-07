## Why

A deploy commit red on its own pipeline while a later pipeline on the deploy branch containing it is green was re-run anyway:
a needless code-host write, and an older build over the newer green one where a failed job deploys.

## What Changes

- The later green is read before the re-run; a deploy standing on it re-runs nothing.

## Capabilities

### Modified Capabilities

- `deploy-check`: a red deploy pipeline is re-run only where no later green contains its commit.
