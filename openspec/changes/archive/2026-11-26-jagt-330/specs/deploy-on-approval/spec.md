## ADDED Requirements

### Requirement: The once-per-arrival guard survives a restart
`DeployOnApprovalJob` SHALL record the arrival it pressed on the task, keyed by the arrival's status stamp, before it presses.
A restart SHALL NOT press the same arrival again; a new arrival at `APPROVED` carries a new stamp and is pressed once.
#### Scenario: A refused arrival after a restart
- **WHEN** the deploy of an `APPROVED` ABC-42 was refused and jagt restarts with ABC-42 still in the same arrival
- **THEN** the job presses nothing for that arrival
