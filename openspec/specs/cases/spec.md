# cases Specification

## Purpose
When jagt may send a session each step of its brief, what it may answer, and what the Master may do. Each is a case
a human reads and extends; what no case takes is a miss.

## Requirements

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

### Requirement: The turn is a fact on the task
`flow/Turns.next` SHALL alone answer who holds a task's turn from its status, report, stop and the Master's mode and
`mine`; the board's owner maps it. A relay SHALL open the session's turn (`task/Turn`) and its next report close it.
Each starts a turn of its own id, which a Master's verdict answers. A step relayed again with the same facts while
that turn is open SHALL send nothing. What the Master pressed, left or failed to answer SHALL stay on the turn, so a
restart repeats none.

#### Scenario: jagt restarts after a deploy
- **WHEN** ABC-42 was deployed, its check asked and another relay followed, and jagt restarts
- **THEN** the Master presses no deploy again and the session is not told again that ABC-42 is deployed

#### Scenario: The same step twice
- **WHEN** the deploy check of ABC-42 is relayed twice before its session reports
- **THEN** the session is told once

#### Scenario: The session goes back to work while the Master reads
- **WHEN** the Master is reading ABC-42's question and its session reports in the meantime
- **THEN** the Master's answer is dropped and nothing sets ABC-42 `IN_PROGRESS`

#### Scenario: A human presses a verb while the Master reads
- **WHEN** the Master is reading ABC-42's hand-back and you press `ship`
- **THEN** its verdict, ready, not ready or a question, is dropped and logged with both turns, whatever it says
- **AND** ABC-42 stays `SHIPPING` and its session is relayed nothing
- **AND** a verdict that stands claims its status and turn in one write, and the session is told after it, outside any lock
- **AND** where the session then cannot be told, the task is put back as it was read, stopped for the human with the cause

#### Scenario: A hand-back made again with no relay between
- **WHEN** the Master judged ABC-42's hand-back, and its session reports IN_PROGRESS then REVIEW_PENDING again
- **THEN** that verdict answers nothing, and the Master reads the new hand-back

### Requirement: What no case names is a miss
A miss SHALL be refused: a step no case names at its status for whoever set it off (`by:` jagt, human or master;
a session's MCP call sets off jagt's), a Master move no case allows or a report no case takes (`by: session`). It
SHALL be logged and written as `{at, task, step, status, by}` to `misses.jsonl` beside `state.json` (rolled at 1 MB).
It SHALL stop the task until a human's `lift`, hover `no case sends <step> at <STATUS>, set off by <by>`. Only a
human extends the cases.

#### Scenario: A step at a status no case names
- **WHEN** a deploy check is relayed to ABC-42 at IN_PROGRESS and `deploy` is sent at DEPLOYED alone
- **THEN** the session hears nothing, `misses.jsonl` holds the line for ABC-42, and the card asks `lift the stop`

#### Scenario: A step set off by someone its case does not name
- **WHEN** you set off `deploy` at DEPLOYED and its only case says `by: jagt`
- **THEN** the session hears nothing and `misses.jsonl` reads `by: human` for it

#### Scenario: A round into a closed task
- **WHEN** a sweep reads open threads on ABC-42 at DONE
- **THEN** nothing is relayed and the sweep says no case sends the round

#### Scenario: The Master meets a stopped task
- **WHEN** ABC-42 stopped on a miss and its session asks a question in `act`
- **THEN** the Master leaves the question to you

#### Scenario: A miss the Master set off
- **WHEN** the Master returns a round to ABC-42 at CI_POLLING
- **THEN** `misses.jsonl` reads `by: master` for it

#### Scenario: A press no case allows
- **WHEN** your copy's `deploy` case says `at: APPROVED` and ABC-42 reaches REVIEWED in `act`
- **THEN** the Master deploys nothing, `misses.jsonl` names `deploy` at REVIEWED by `master`, and the card asks `lift the stop`
- **AND** `master.mine` and `humanPaths` still keep a move the human's where a case allows it

#### Scenario: A report is applied where a case takes it
- **WHEN** a report arrives while a turn is open
- **THEN** it is applied only where a case naming the turn's step takes its status and kind
- **AND** a turn opened by joined steps takes what any of their cases takes
- **AND** a report with no turn open, or to a step no case names, is applied as before

#### Scenario: A reply the case takes
- **WHEN** ABC-42's deploy check is open and its session reports IN_PROGRESS saying what broke
- **THEN** ABC-42 goes back to work

#### Scenario: A reply no case takes
- **WHEN** ABC-42's deploy check is open and its session reports CI_POLLING
- **THEN** the report is refused and not applied, the turn passes to the human, and the card asks `lift the stop`
- **AND** `misses.jsonl` names `deploy` with `by: session`, `got` and `expected`
