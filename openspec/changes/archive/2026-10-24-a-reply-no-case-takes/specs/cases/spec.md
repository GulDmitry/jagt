## MODIFIED Requirements

### Requirement: The cases are a file you may copy
A step jagt relays SHALL reach a session only where a case of `session-cases.md` beside `jagt.yml` names it at the
task's status. The copy comes from `session-cases.md.dist`; a case it lacks SHALL be read from the `.dist`. A case
is a `## <id>` section holding `step:`, `at:`, `by:`, `replies:` and one line saying what it is, each key as the
`.dist` describes it.

#### Scenario: Nothing copied
- **WHEN** no `session-cases.md` exists
- **THEN** every step is sent where `session-cases.md.dist` says

#### Scenario: A copy narrows a case
- **WHEN** your copy's `wake` case says `at: IN_PROGRESS`
- **THEN** a session stopped on a usage limit at REVIEW_PENDING is not woken

### Requirement: Startup checks the cases against the brief both ways
jagt SHALL refuse to start on a case naming a step no brief section holds, or a status, `by` or reply kind that is
none. It SHALL refuse a case with no `replies:` line or no line saying what it is. It SHALL refuse an id written
twice, and a brief step no case sends. Each refusal names the file and the case.

#### Scenario: A case naming a missing step
- **WHEN** a case says `step: woke` and the brief has no `## woke`
- **THEN** jagt does not start and names `session-cases.md` and that case

#### Scenario: A brief step no case sends
- **WHEN** the brief has `## deploy` and no case names it
- **THEN** jagt does not start and names the step

#### Scenario: A case naming no replies
- **WHEN** the `wake` case has no `replies:` line, or one naming the kind `maybe`
- **THEN** jagt does not start and names `session-cases.md` and that case

## ADDED Requirements

### Requirement: A reply no case takes is a miss
A session's report while its turn is open SHALL be applied only where a case naming the turn's step takes its
status and its kind; a turn opened by joined steps takes what any of their cases takes. Any other SHALL be refused,
not applied, and written to `misses.jsonl` as `{at, task, step, status, by: session, got, expected}`. It SHALL stop
the task as a step no case sends does, and pass the turn to the human. A report with no turn open, or to a step no case
names, SHALL be applied as before.

#### Scenario: A reply the case takes
- **WHEN** ABC-42's deploy check is open and its session reports IN_PROGRESS saying what broke
- **THEN** ABC-42 goes back to work

#### Scenario: A reply no case takes
- **WHEN** ABC-42's deploy check is open and its session reports CI_POLLING
- **THEN** the report is refused and not applied, `misses.jsonl` names `deploy`, what came and what it takes, and
  the card asks `lift the stop`
