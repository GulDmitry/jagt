## MODIFIED Requirements

### Requirement: A checker declares its jobs and its read steps
A checker's `role.yml` SHALL declare `jobs:` (true = it runs jobs) and `readSteps:` (the brief steps whose turn opens its read).
The shipped acceptance declares `jobs: true` and `readSteps: [accept, again, decline]`. Two installed checkers naming one read step SHALL be refused.
`readSteps` are in order the check, its run again and its decline; each checker declaring `jobs: true` SHALL run `<id>-check`, `-wait` and `-decline`.

#### Scenario: acceptance's read opens
- **WHEN** ABC-42 is DEPLOYED and the session is relayed to `accept`, `again` or `decline`
- **THEN** acceptance's read opens, as its `role.yml.dist` declares

#### Scenario: Two roles name one read step
- **WHEN** `acceptance` and a second checker both name `decline` in `readSteps`
- **THEN** jagt refuses the composition at startup, naming `decline`, `acceptance` and the second checker

#### Scenario: A second checker is installed
- **WHEN** `roles/audit/role.yml` declares `jobs: true` and ABC-42 deploys **THEN** audit's check runs beside acceptance's, on its own step