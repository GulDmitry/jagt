## Why

A session of a two-repository task ran Compose in its second repository's worktree under the first repository's project name, so a duplicate stack started and its services failed.

## What Changes

- A compose command is named `<repository>-<task>` of the worktree it runs in, wherever the session started.

## Capabilities

### New Capabilities

### Modified Capabilities

- `platform`: a stack's project name follows the worktree the command runs in.
