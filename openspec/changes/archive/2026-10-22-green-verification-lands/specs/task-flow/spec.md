## MODIFIED Requirements

### Requirement: A hand-back waits at VERIFYING
`FlowRules.reported` SHALL redirect a hand-back owing a verification run to `VERIFYING`; no action leads there and
no agent may report it. `VerifyJob` runs the command: green reaches the human, red MUST go back to the session with
the output. `FlowRules.awaitingVerification` keeps the watchdog off that silence.

#### Scenario: A red verification run
- **WHEN** `verifyCommand` goes red
- **THEN** the session gets it, not you

#### Scenario: A green verification run
- **WHEN** `verifyCommand` and every probe go green on a task at `VERIFYING`
- **THEN** it lands `REVIEW_PENDING` and is not verified again until the next hand-back

#### Scenario: Verification before the Master
- **WHEN** a hand-back is `VERIFYING` and the Master runs
- **THEN** you and the session hear: verification first, then the Master
