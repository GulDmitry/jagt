## ADDED Requirements

### Requirement: The Master's moves are a file you may copy
A Master move is a verb it presses (`ship`, `deploy`, `approve-plan`, `add-repo`), the replies on its ship, or a step
it relays. Each SHALL be named by a case of `master-cases.md`; a case it lacks SHALL be read from `master-cases.md.dist`.
A case holds `move:`, `at:`, `by: master` and one line saying what it is.

#### Scenario: Nothing copied
- **WHEN** no `master-cases.md` exists
- **THEN** the Master moves where `master-cases.md.dist` says

### Requirement: A Master move no case allows is a miss
A Master move at a status no case names SHALL NOT be made. It SHALL be written to `misses.jsonl` as
`{at, task, step, status, by: master}` and stop the task as a step no case sends does. `master.mine` and `humanPaths`
SHALL still keep a move the human's where a case allows it.

#### Scenario: A press no case allows
- **WHEN** your copy's `deploy` case says `at: APPROVED` and ABC-42 reaches REVIEWED in `act`
- **THEN** the Master deploys nothing, `misses.jsonl` names `deploy` at REVIEWED by `master`, and the card asks `lift the stop`

### Requirement: Startup checks the Master's cases against its moves both ways
jagt SHALL refuse to start on a Master case naming a move the Master lacks, a status that is none, or a `by` not
`master`. It SHALL refuse a Master move no case names. Each refusal names the file and the case or move, machine
checks off or not.

#### Scenario: A revert
- **WHEN** a case names `move: revert`
- **THEN** jagt does not start: the Master never reverts

#### Scenario: A move no case names
- **WHEN** no case names `answered-under`
- **THEN** jagt does not start and names `master-cases.md` and `answered-under`
