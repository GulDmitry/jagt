# cases Specification

## Purpose
When jagt may send a session each step of its brief, as cases a human reads and extends; a step no case sends is a miss.

## Requirements

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
logged. It SHALL stop the task as a suspected loop does, its hover `no case sends <step> at <STATUS>, set off by
<by>`, until a human's `lift`. The Master SHALL NOT decide a miss; a human extends the cases.

#### Scenario: A step at a status no case names
- **WHEN** a deploy check is relayed to ABC-42 at IN_PROGRESS and `deploy` is sent at DEPLOYED alone
- **THEN** the session hears nothing, `misses.jsonl` holds the line for ABC-42, and the card asks `lift the stop`

#### Scenario: A round into a closed task
- **WHEN** a sweep reads open threads on ABC-42 at DONE
- **THEN** nothing is relayed and the sweep says no case sends the round

#### Scenario: The Master meets a stopped task
- **WHEN** ABC-42 stopped on a miss and its session asks a question in `act`
- **THEN** the Master leaves the question to you

#### Scenario: A miss the Master set off
- **WHEN** the Master returns a round to ABC-42 at CI_POLLING
- **THEN** `misses.jsonl` reads `by: master` for it

### Requirement: The turn is a fact on the task
`flow/Turns.next` SHALL alone answer who holds a task's turn from its status, its report and the Master's mode and
`mine`. A relay SHALL open the session's turn on the task (`task/Turn`: step, digest of its facts, `since`); the
session's next report SHALL close it. A step relayed again with the same facts while that turn is open SHALL send
nothing. What the Master pressed, left or failed to answer SHALL be kept on the turn, so a restart repeats none.

#### Scenario: jagt restarts after a deploy
- **WHEN** ABC-42 was deployed, its check asked and another relay followed, and jagt restarts
- **THEN** the Master presses no deploy again and the session is not told again that ABC-42 is deployed

#### Scenario: The same step twice
- **WHEN** the deploy check of ABC-42 is relayed twice before its session reports
- **THEN** the session is told once

#### Scenario: The session goes back to work while the Master reads
- **WHEN** the Master is reading ABC-42's question and its session reports in the meantime
- **THEN** the Master's answer is dropped and nothing sets ABC-42 `IN_PROGRESS`
