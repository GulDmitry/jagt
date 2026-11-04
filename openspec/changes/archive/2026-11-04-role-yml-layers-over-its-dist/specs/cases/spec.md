## ADDED Requirements

### Requirement: A role.yml copy layers over its .dist
A `roles/<id>/role.yml` SHALL be read over its `role.yml.dist` key by key, nested mappings too, a list or value it
writes replacing the shipped one whole; refused, the `.dist` alone SHALL stand, startup noting why (`InstalledRoles`).

#### Scenario: A copy writes some keys
- **WHEN** `roles/qa/role.yml` holds `model: opus`, `trigger: [deployed]` and `tools: {git: false}`
- **THEN** QA runs on opus without git, triggered by `deployed` alone, every other key and tool list the `.dist`'s

#### Scenario: A copy that breaks the role
- **WHEN** `roles/qa/role.yml` holds `outcomes: []`
- **THEN** QA runs as the `.dist` says, and startup notes `role.yml` refused with the shipped file standing
