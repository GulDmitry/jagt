## MODIFIED Requirements

### Requirement: The cases are files you may copy
A step jagt relays SHALL reach a session only where a case of `session-cases.md` beside `jagt.yml` names it at the
task's status; a verb the Master presses, or the replies on its ship, only where a case of `master-cases.md` does.
A step the Master relays SHALL take its statuses from its session case `by: master` alone.
A case a copy lacks SHALL be read from its `.dist`. A case is a `## <id>` section holding
one line saying what it is and the keys its `.dist` describes.

#### Scenario: Nothing copied
- **WHEN** no `session-cases.md` or `master-cases.md` exists
- **THEN** every step is sent and every move made where the `.dist` says

#### Scenario: A copy narrows a case
- **WHEN** your copy's `wake` case says `at: IN_PROGRESS`
- **THEN** a session stopped on a usage limit at REVIEW_PENDING is not woken

#### Scenario: The keys of a case
- **WHEN** a case is written
- **THEN** a session case holds `step:`, `at:`, `by:` and `replies:`
- **AND** a Master case holds `move:`, `at:` and `by: master`

#### Scenario: A step the Master relays
- **WHEN** your `session-cases.md` sends `answered` by master at IN_PROGRESS and ABC-42 is IN_PROGRESS
- **THEN** the Master's answer reaches the session, whatever `master-cases.md` holds

### Requirement: Startup checks the cases against the code both ways
jagt SHALL refuse to start on a case naming no brief step, or a Master case naming no verb it presses.
It SHALL refuse a status, `by` or reply kind that is none, and a Master `by` not `master`.
It SHALL refuse a case missing `replies:` or the line saying what it is, and an id written twice.
It SHALL refuse a brief step or Master verb no case names, and a relayed Master step no `by: master` case names.
It SHALL refuse a file it cannot read. Each refusal names the file and the case or move.

#### Scenario: A case naming a missing step
- **WHEN** a case says `step: woke` and the brief has no `## woke`
- **THEN** jagt does not start and names `session-cases.md` and that case

#### Scenario: A brief step no case sends
- **WHEN** the brief has `## deploy` and no case names it
- **THEN** jagt does not start and names the step

#### Scenario: A case naming no replies
- **WHEN** the `wake` case has no `replies:` line, or one naming the kind `maybe`
- **THEN** jagt does not start and names `session-cases.md` and that case

#### Scenario: A file that cannot be read
- **WHEN** your copy of `session-cases.md` exists and cannot be read
- **THEN** jagt does not start and names the file and the cause, and the `.dist` does not stand in for it

#### Scenario: A revert
- **WHEN** a case names `move: revert`
- **THEN** jagt does not start: the Master never reverts

#### Scenario: A move no case names
- **WHEN** no Master case names `add-repo`
- **THEN** jagt does not start and names `master-cases.md` and `add-repo`

#### Scenario: A relayed step in the Master's cases
- **WHEN** a Master case names `move: answered`
- **THEN** jagt does not start and names `master-cases.md` and that case

#### Scenario: A relayed step no session case sets off by the Master
- **WHEN** no session case names `answered-under` with `by: master`
- **THEN** jagt does not start and names `session-cases.md` and `answered-under`
