## MODIFIED Requirements

### Requirement: A hand-back waits at VERIFYING
`FlowRules.reported` SHALL redirect a hand-back owing a verification run to `VERIFYING`; no action leads there and
no agent may report it. `VerifyJob` runs the command: green reaches the human, red MUST go back to the session with
the output. `FlowRules.awaitingVerification` keeps the watchdog off that silence.

#### Scenario: A verification run
- **WHEN** `verifyCommand` and the probes run on a task at `VERIFYING`
- **THEN** green lands `REVIEW_PENDING` and runs no more; red goes to the session, not you

#### Scenario: Verification before the Master
- **WHEN** a hand-back is `VERIFYING` and the Master runs
- **THEN** you and the session hear: verification first, then the Master
