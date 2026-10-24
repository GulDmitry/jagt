## MODIFIED Requirements

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
