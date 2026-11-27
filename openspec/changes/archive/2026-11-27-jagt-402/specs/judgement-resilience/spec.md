## ADDED Requirements

### Requirement: A ship turn ends in CI_POLLING or a question
A session's report ending a `ship` turn of a task whose request is open SHALL be CI_POLLING, a question or a permission.
`FlowRules.refusedAtShip` MUST refuse any other, naming CI_POLLING; a ship with no request open hands back as its report line says.

#### Scenario: A clean tree shipped with its request green
- **WHEN** ABC-42's request is open with its pipeline green, its tree is clean, and its session answers `ship` with REVIEW_PENDING
- **THEN** the report is refused naming CI_POLLING, and ABC-42 stays SHIPPING, never VERIFYING

### Requirement: A role's eval runs when its brief changes
`roleEval -Prole=session` SHALL read each lifecycle step of `roles/session/brief.md.dist` against `roles/session/eval/*.yml`,
each a step's facts, what the session's commands showed and the report expected. `testsFor` SHALL name `roleEval -Prole=<id>`
for a change to a role's `.dist` file or eval case, where `roles/<id>/eval` exists.

#### Scenario: The session brief changes
- **WHEN** `roles/session/brief.md.dist` changes
- **THEN** `testsFor` names `roleEval -Prole=session`
- **AND** its ship cases expect CI_POLLING: a clean green tree, a retried push, uncommitted changes, a running pipeline
