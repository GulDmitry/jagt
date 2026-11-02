## MODIFIED Requirements

### Requirement: The cases are files you may copy
A step jagt relays SHALL reach a session only where a case of the `session` role's `cases.md` names it at the
task's status; a verb the Master presses, or the replies on its ship, only where a case of the `master` role's does.
A role's `cases.md` SHALL be read from, first that exists: `<id>-cases.md` beside `jagt.yml`, `roles/<id>/cases.md`,
`roles/<id>/cases.md.dist`.
A step the Master relays SHALL take its statuses from its session case `by: master` alone.
A case a copy lacks SHALL be read from its `.dist`. A case is a `## <id>` section holding
one line saying what it is and the keys its `.dist` describes.

#### Scenario: Nothing copied
- **WHEN** no copy of the session's or the Master's `cases.md` exists
- **THEN** every step is sent and every move made where `roles/<id>/cases.md.dist` says

#### Scenario: An old copy beside jagt.yml
- **WHEN** `session-cases.md` sits beside `jagt.yml` and `roles/session/cases.md` exists too
- **THEN** `session-cases.md` is read

#### Scenario: A copy narrows a case
- **WHEN** your copy's `wake` case says `at: IN_PROGRESS`
- **THEN** a session stopped on a usage limit at REVIEW_PENDING is not woken

#### Scenario: The keys of a case
- **WHEN** a case is written
- **THEN** a session case holds `step:`, `at:`, `by:` and `replies:`
- **AND** a Master case holds `move:`, `at:` and `by: master`

#### Scenario: A step the Master relays
- **WHEN** your session `cases.md` sends `answered` by master at IN_PROGRESS and ABC-42 is IN_PROGRESS
- **THEN** the Master's answer reaches the session, whatever the Master's `cases.md` holds
