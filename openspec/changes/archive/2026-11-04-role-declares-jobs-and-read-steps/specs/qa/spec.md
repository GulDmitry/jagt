## ADDED Requirements

### Requirement: A checker declares its jobs and its read steps
A checker's `role.yml` SHALL declare `jobs:` (true = it runs jobs) and `readSteps:` (the brief steps whose turn opens its read).
The shipped QA declares `jobs: true` and `readSteps: [accept, again, decline]`.
Two installed checkers naming one read step SHALL be refused.

#### Scenario: QA's read opens
- **WHEN** ABC-42 is DEPLOYED and the session is relayed to `accept`, `again` or `decline`
- **THEN** QA's read opens, as its `role.yml.dist` declares

#### Scenario: Two roles name one read step
- **WHEN** `qa` and a second checker both name `decline` in `readSteps`
- **THEN** jagt refuses the composition at startup, naming `decline`, `qa` and the second checker
