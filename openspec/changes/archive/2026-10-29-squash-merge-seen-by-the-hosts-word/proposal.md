## Why

A request merged by squash or fast-forward was never seen merged: git finds only a merge commit naming the tip, while the host said merged.

## What Changes

- The review read carries the host's `merged` word; a request it reports merged is recorded merged like one git found.
- `ship` is refused on it as on any merged request.

## Capabilities

### Modified Capabilities

- `git`: a request is merged by the host's word or by git.
