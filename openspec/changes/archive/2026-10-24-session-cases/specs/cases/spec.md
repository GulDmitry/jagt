## ADDED Requirements

### Requirement: The cases are a file you may copy
A step jagt relays SHALL reach a session only where a case of `session-cases.md` beside `jagt.yml` names it at the
task's status. The copy comes from `session-cases.md.dist`; a case it lacks SHALL be read from the `.dist`. A case
is a `## <id>` section holding `step:`, `at:` (statuses, `any` or `any but <statuses>`), `by:` (jagt, master or
human) and one line saying what it is.

#### Scenario: Nothing copied
- **WHEN** no `session-cases.md` exists
- **THEN** every step is sent where `session-cases.md.dist` says

#### Scenario: A copy narrows a case
- **WHEN** your copy's `wake` case says `at: IN_PROGRESS`
- **THEN** a session stopped on a usage limit at REVIEW_PENDING is not woken

### Requirement: Startup checks the cases against the brief both ways
jagt SHALL refuse to start on a case naming a step no brief section holds, or a status or `by` that is none. It
SHALL refuse a case with no line saying what it is, an id written twice, and a brief step no case sends. Each
refusal names the file and the case.

#### Scenario: A case naming a missing step
- **WHEN** a case says `step: woke` and the brief has no `## woke`
- **THEN** jagt does not start and names `session-cases.md` and that case

#### Scenario: A brief step no case sends
- **WHEN** the brief has `## deploy` and no case names it
- **THEN** jagt does not start and names the step

### Requirement: A step no case sends is a miss
A step at a status no case names SHALL NOT reach the session. It SHALL be written as one line,
`{at, task, step, status, by}`, to `misses.jsonl` beside `state.json`, rolled at 1 MB into `misses.jsonl.1`, and
logged. The Master SHALL NOT decide a miss; a human extends the cases.

#### Scenario: A step at a status no case names
- **WHEN** a deploy check is relayed to ABC-42 at IN_PROGRESS and `deploy` is sent at DEPLOYED alone
- **THEN** the session hears nothing and `misses.jsonl` holds the line for ABC-42

#### Scenario: A round into a closed task
- **WHEN** a sweep reads open threads on ABC-42 at DONE
- **THEN** nothing is relayed and the sweep says no case sends the round
