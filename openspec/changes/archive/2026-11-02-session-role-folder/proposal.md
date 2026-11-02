## Why

One place per role: the session's copy of a file lives in `roles/session/`, beside its `.dist`
([0126](../../../docs/decisions/0126-a-role-is-a-folder.md)).

## What Changes

- The session's brief and cases ship in `roles/session/` and are read from there alone.
- The root names `session-brief.md` and `session-cases.md` are no longer read.

## Capabilities

### Modified Capabilities

- `cases`: the session's `cases.md` is read from its folder alone.
- `session-brief`: the session's `brief.md` is read from its folder alone.
