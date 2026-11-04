## Why

A session told nothing of `repo=` probes another repository through a path, and the Master does not stop it.

## What Changes

- The session brief's plan step names `command [repo=<key>] [exit=<n>] <argv>` and where it runs.
- The Master's plan read flags a probe reaching another repository by a path, pattern `misplaced probe`.

## Capabilities

### Modified Capabilities

- `acceptance-probes`: the Master reads probes as facts.
