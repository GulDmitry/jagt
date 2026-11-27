# judgement-resilience Specification

## Purpose

How jagt holds a model's answer steady: repeated runs, what the machine heard, yes-or-no criteria.

## Requirements

### Requirement: An eval case passes on a majority of runs
A model eval case SHALL run up to `-PevalRuns` times (3) and pass when `-PevalPasses` runs do (a majority), stopping
once that is decided. A failing case SHALL report every failed run's own message. `-PevalPasses` above `-PevalRuns`
SHALL be refused.

#### Scenario: One run flakes
- **WHEN** one run in three fails
- **THEN** the case passes

#### Scenario: Two runs fail
- **WHEN** two runs in three fail
- **THEN** the case fails, carrying both failures' messages

#### Scenario: The outcome is decided
- **WHEN** the first two runs pass
- **THEN** no third run is made

### Requirement: An eval asserts what jagt heard, not only what the model said
Each `roleEval -Prole=master` run SHALL be asserted on what the read gate heard: a call made in that run's worktree. A refused call
SHALL NOT fail the run; refusing is the gate's work.

#### Scenario: The round was read
- **WHEN** a run ends
- **THEN** a heard call ran in its worktree, else the run fails printing the heard calls

#### Scenario: A call names no path
- **WHEN** the Master runs `git log` with no path inside the worktree
- **THEN** that call counts as the run's

#### Scenario: A call was refused
- **WHEN** the gate refused a call of the run and another call ran in its worktree
- **THEN** the run is judged on its verdict alone

### Requirement: A pattern the Master stops on is one yes-or-no fact
A pattern line in `roles/master/brief.md.dist` SHALL state one fact a reader answers yes or no on the diff, with no scale
or taste word (`PatternLinesTest`).

#### Scenario: A taste word
- **WHEN** a pattern line says "properly"
- **THEN** `PatternLinesTest` fails

### Requirement: A ship turn ends in CI_POLLING or a question
A session's report ending a `ship` turn of a task whose request is open SHALL be CI_POLLING, a question or a permission.
`FlowRules.refusedAtShip` MUST refuse any other, naming CI_POLLING; a ship with no request open hands back as its report line says.

#### Scenario: A clean tree shipped with its request green
- **WHEN** ABC-42's request is open with its pipeline green, its tree is clean, and its session answers `ship` with REVIEW_PENDING
- **THEN** the report is refused naming CI_POLLING, and ABC-42 stays SHIPPING, never VERIFYING

### Requirement: A role's eval runs when its brief changes
`roleEval -Prole=session` SHALL read each lifecycle step of `roles/session/brief.md.dist` against `roles/session/eval/*.yml`,
each a step's facts, what the session's commands showed and the report expected. `testsFor` SHALL name `roleEval -Prole=<id>`
for a change to a role's `.dist` file or eval case, where `roles/<id>/eval` exists.

#### Scenario: The session brief changes
- **WHEN** `roles/session/brief.md.dist` changes
- **THEN** `testsFor` names `roleEval -Prole=session`
- **AND** its ship cases expect CI_POLLING: a clean green tree, a retried push, uncommitted changes, a running pipeline
