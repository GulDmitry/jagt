## Why

One place per role: the Master's copy of a file lives in `roles/master/`, beside its `.dist`
([0126](../../../docs/decisions/0126-a-role-is-a-folder.md)).

## What Changes

- The root names `master-brief.md` and `master-cases.md` are no longer read.

## Capabilities

### Modified Capabilities

- `cases`: the Master's `cases.md` is read from its folder alone.
- `master`: the Master's brief is read from its folder alone.
