## Why

A repository that never landed, its project since removed from `jagt.yml`, made `revert` from DEPLOY_CONFLICT throw
before anything was taken out.

## What Changes

- The waiting half-merge is looked for only in repositories `jagt.yml` still names.

## Capabilities

### Modified Capabilities

- `git`: revert from a conflict no longer needs every repository's project configured.
