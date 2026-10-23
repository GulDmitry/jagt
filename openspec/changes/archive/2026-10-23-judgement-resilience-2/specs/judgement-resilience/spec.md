## MODIFIED Requirements

### Requirement: An eval asserts what jagt heard, not only what the model said
Each `masterEval` run SHALL be asserted on what the read gate heard: a call naming that run's worktree. A refused call
SHALL NOT fail the run; refusing is the gate's work.

#### Scenario: The round was read
- **WHEN** a run ends
- **THEN** a heard call names its worktree, else the run fails printing the heard calls

#### Scenario: A call was refused
- **WHEN** the gate refused a call of the run and the run read its worktree
- **THEN** the run is judged on its verdict alone
