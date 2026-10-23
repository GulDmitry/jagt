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
Each `masterEval` run SHALL be asserted on what the read gate heard: a call naming that run's worktree. A refused call
SHALL NOT fail the run; refusing is the gate's work.

#### Scenario: The round was read
- **WHEN** a run ends
- **THEN** a heard call names its worktree, else the run fails printing the heard calls

#### Scenario: A call was refused
- **WHEN** the gate refused a call of the run and the run read its worktree
- **THEN** the run is judged on its verdict alone

### Requirement: A pattern the Master stops on is one yes-or-no fact
A pattern line in `master-brief.md.dist` SHALL state one fact a reader answers yes or no on the diff, with no scale
or taste word (`PatternLinesTest`).

#### Scenario: A taste word
- **WHEN** a pattern line says "properly"
- **THEN** `PatternLinesTest` fails
