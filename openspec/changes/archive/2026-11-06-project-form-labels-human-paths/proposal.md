## Why

A project's `labels` and `humanPaths` could only be written into `jagt.yml` by hand.

## What Changes

- The add and edit form carries `labels` and `humanPaths`, each typed as a comma-separated list; both become keys the
  form rewrites.

## Capabilities

### Modified Capabilities

- `project-setup`: the form writes `labels` and `humanPaths`.
