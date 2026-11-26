## ADDED Requirements

### Requirement: Deploy waits for green checks on the head
At `REVIEWED` or `APPROVED`, `FlowRules` SHALL offer `deploy` only while the checks read green on the request's head, no read failing since
(`Facts.checksGreen`); a head listing no pipeline counts as green. A re-run or a failed read withdraws it until the next green read.
Until then the request is the host's turn (`Turns`), and `MasterDeployJob` and `DeployOnApprovalJob` wait before their one press per arrival.

#### Scenario: A re-run after green
- **WHEN** ABC-42 is `REVIEWED` or `APPROVED` and a read finds its checks running again
- **THEN** `deploy` is not offered until the next green read

#### Scenario: A failed read after green
- **WHEN** ABC-42 is `REVIEWED` or `APPROVED` and the read after its green cannot list the checks
- **THEN** `deploy` is not offered until the next green read

#### Scenario: Approved while the checks run
- **WHEN** the host approves ABC-42 while its checks run, with `deploy.onApproval` on or the Master in `act`
- **THEN** nothing is pressed until the checks read green; then `deploy` is pressed once

#### Scenario: A request with no pipeline
- **WHEN** the host lists no pipeline for ABC-42's head, which is `REVIEWED` or `APPROVED`
- **THEN** `deploy` is offered: nothing is left to wait for
