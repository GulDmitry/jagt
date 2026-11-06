## Why

A role's `tools` carried Claude Code tool and permission syntax, so a role was bound to one CLI.

## What Changes

- **BREAKING** `tools.cli`, `tools.allow` and `tools.refuse` are `tools.shell`, `tools.refuseShell` and `tools.refuseRead`: command and path globs.
- Reads are always on and writes always refused; each CLI's adapter turns the globs into its own syntax.
- A `role.yml` still carrying an old key is refused, naming the new ones.

## Capabilities

### Modified Capabilities

- `qa`: a role's tool scope is CLI-neutral.
