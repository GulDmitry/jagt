## ADDED Requirements

### Requirement: The workspace's layout is the role folder's
`workspace.index`, `scenarios`, `steps` and `flows` SHALL place the index and each folder, relative to the workspace.
The brief SHALL name them as `{workspace.<key>}`; unset, they are `index.md`, `scenarios`, `steps` and `flows`.

#### Scenario: A suite with its own layout
- **WHEN** `roles/acceptance/role.yml` maps `workspace.index` to `wiki/index.md` and the brief is the shipped one
- **THEN** the acceptance read opens `wiki/index.md` first, with no brief override
