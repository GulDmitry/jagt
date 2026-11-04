## Why

A human's `role.yml` replaced its `.dist` whole, so changing one key meant copying every other one, which then went stale.

## What Changes

- A `roles/<id>/role.yml` layers over its `role.yml.dist` per key, nested mappings too; a list it writes replaces whole.
- Where the layered file does not compile, the `.dist` alone stands and startup notes why.

## Capabilities

### Modified Capabilities

- `cases`: a role's `role.yml` copy is layered like its `cases.md` copy.
