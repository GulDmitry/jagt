## ADDED Requirements

### Requirement: A role names its tools as CLI-neutral globs
A role's `tools` SHALL name `shell` and `refuseShell`, the command globs a read may and may not run.
It SHALL name `refuseRead`, the path globs no read may open, beside `git` and `fetch`.
Reads are always on and writes always refused.
A `role.yml` carrying `cli`, `allow` or `refuse` under `tools` SHALL be refused, naming the new keys.

#### Scenario: A moved key
- **WHEN** `roles/qa/role.yml` holds `tools: {allow: [Read]}`
- **THEN** the role is refused, naming `tools.shell`, `tools.refuseShell` and `tools.refuseRead`
