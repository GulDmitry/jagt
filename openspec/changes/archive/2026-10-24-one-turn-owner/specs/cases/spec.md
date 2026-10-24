## MODIFIED Requirements

### Requirement: The turn is a fact on the task
`flow/Turns.next` SHALL alone answer who holds a task's turn from its status, report, stop and the Master's mode and
`mine`; the board's owner maps it. A relay SHALL open the session's turn on the task (`task/Turn`: step, digest of its
facts, `since`); the session's next report SHALL close it. A step relayed again with the same facts while that turn
is open SHALL send nothing. What the Master pressed, left or failed to answer SHALL be kept on the turn, so a restart
repeats none.

#### Scenario: jagt restarts after a deploy
- **WHEN** ABC-42 was deployed, its check asked and another relay followed, and jagt restarts
- **THEN** the Master presses no deploy again and the session is not told again that ABC-42 is deployed

#### Scenario: The same step twice
- **WHEN** the deploy check of ABC-42 is relayed twice before its session reports
- **THEN** the session is told once

#### Scenario: The session goes back to work while the Master reads
- **WHEN** the Master is reading ABC-42's question and its session reports in the meantime
- **THEN** the Master's answer is dropped and nothing sets ABC-42 `IN_PROGRESS`

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
