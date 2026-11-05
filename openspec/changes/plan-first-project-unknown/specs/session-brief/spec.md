## ADDED Requirements

### Requirement: A task with no repository plans its repositories
The brief of a task with no repository SHALL tell the session it plans in a scratch folder, and that its plan's `## Repositories`
names every project it changes, registered in `jagt.yml` or not.

#### Scenario: A plan-first session with no project
- **WHEN** such a task's session is briefed
- **THEN** it reads that `plan.md` needs `## Repositories` with a `- [changed] <key>` per project
