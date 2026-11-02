## MODIFIED Requirements

### Requirement: The cases are files you may copy
A step jagt relays SHALL reach a session only where a case of `session-cases.md` beside `jagt.yml` names it at the
task's status; a verb the Master presses, or the replies on its ship, only where a case of the `master` role's
`cases.md` does: `master-cases.md` beside `jagt.yml`, `roles/master/cases.md`, `roles/master/cases.md.dist`.
A step the Master relays SHALL take its statuses from its session case `by: master` alone.
A case a copy lacks SHALL be read from its `.dist`. A case is a `## <id>` section holding
one line saying what it is and the keys its `.dist` describes.

#### Scenario: Nothing copied
- **WHEN** no copy of `session-cases.md` or of the Master's `cases.md` exists
- **THEN** every step is sent and every move made where the `.dist` says

#### Scenario: An old copy beside jagt.yml
- **WHEN** `master-cases.md` sits beside `jagt.yml` and `roles/master/cases.md` exists too
- **THEN** a case `master-cases.md` holds is read from it

#### Scenario: A copy narrows a case
- **WHEN** your copy's `wake` case says `at: IN_PROGRESS`
- **THEN** a session stopped on a usage limit at REVIEW_PENDING is not woken

#### Scenario: The keys of a case
- **WHEN** a case is written
- **THEN** a session case holds `step:`, `at:`, `by:` and `replies:`
- **AND** a Master case holds `move:`, `at:` and `by: master`

#### Scenario: A step the Master relays
- **WHEN** your `session-cases.md` sends `answered` by master at IN_PROGRESS and ABC-42 is IN_PROGRESS
- **THEN** the Master's answer reaches the session, whatever the Master's `cases.md` holds
