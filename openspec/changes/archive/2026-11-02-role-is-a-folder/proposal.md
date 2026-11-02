## Why

A role's brief, cases, config and evals are spread over the root, `jagt.yml` and Java source sets; only QA has a
folder. A role added or switched off should be one folder ([0126](../../../docs/decisions/0126-a-role-is-a-folder.md)).

## What Changes

- The Master's brief and cases ship in `roles/master/`, read through `RoleFiles`; the session follows in a later change.
- An old copy beside `jagt.yml` (`master-brief.md`, `master-cases.md`) is still read first.
- `roles.master.brief` defaults to `roles/master/brief.md`.

## Capabilities

### Modified Capabilities

- `cases`: the Master's `cases.md` is read from its folder, an old copy first.
- `master`: the Master's brief is read from `roles/master/`, an old copy first.
