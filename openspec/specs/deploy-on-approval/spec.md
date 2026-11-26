# deploy-on-approval Specification

## Purpose
A request the code host approved is deployed once without a human's press, where the install turns it on.

## Requirements

### Requirement: An approved request deploys itself when deploy.onApproval is on
With `deploy.onApproval` on, `DeployOnApprovalJob` SHALL press the one `deploy` a human's press runs. It presses once per arrival
at `APPROVED`, every guard of that deploy holding. The history names `ActionOrigin.CODE_HOST` as who acted
([0201](../../../docs/decisions/0201-an-approved-request-deploys-itself-when-deploy-on-approval-is-on.md)).
It is `false` by default and `true` in `jagt.server.yml.dist`.

#### Scenario: An approved request on a server
- **WHEN** `deploy.onApproval` is on, no Master runs, and the code host approves ABC-42's request
- **THEN** ABC-42 is deployed and its deploy check runs
- **AND** its history names the code host as who deployed

#### Scenario: A request waits weeks for an approval
- **WHEN** ABC-42 is `REVIEWED` and weeks pass with no approval
- **THEN** it is read every 60 minutes without end, owned by CI, and asks no attention

#### Scenario: The switch is off
- **WHEN** `deploy.onApproval` is off, its default, and ABC-42 is `APPROVED`
- **THEN** it waits for a human's or the Master's `deploy`

#### Scenario: Someone deployed first
- **WHEN** a human or the Master deployed ABC-42 before the job read it
- **THEN** the job presses nothing

#### Scenario: The deploy is refused
- **WHEN** the deploy of an `APPROVED` ABC-42 is refused
- **THEN** one WARN `deploy on approval refused` carries the cause
- **AND** the job does not press again for that arrival

#### Scenario: The deploy conflicts
- **WHEN** the approved ABC-42's merge conflicts
- **THEN** it stands in `DEPLOY_CONFLICT` for a human

### Requirement: Deploy is offered only on an approved request when deploy.requireApproval is on
With `deploy.requireApproval` on, `FlowRules` SHALL offer `deploy` on a request only at `APPROVED`, never at `REVIEWED`.
The human, the Master in `act` and every surface are alike. A `deploy` press at `REVIEWED` SHALL be refused with
`ACTION_NOT_AVAILABLE` and a sentence naming `deploy.requireApproval`. `deploy.onApproval` is unchanged.
The key is `false` by default and `true` in `jagt.server.yml.dist`; `jagt.desktop.yml.dist` describes it, off.

#### Scenario: A reviewed request is not deployable
- **WHEN** `deploy.requireApproval` is on and ABC-42 is `REVIEWED`
- **THEN** no `deploy` is offered on its card
- **AND** a `deploy` press (board, palette or MCP `deploy_task`) is refused naming `deploy.requireApproval`

#### Scenario: The host approves
- **WHEN** `deploy.requireApproval` is on and the code host approves ABC-42's request
- **THEN** `deploy` is offered on ABC-42
- **AND** with `deploy.onApproval` on it is pressed once

#### Scenario: The Master in act reads a reviewed request
- **WHEN** `deploy.requireApproval` is on, the Master runs in `act` and ABC-42 is `REVIEWED`
- **THEN** the Master does not deploy it
- **AND** the card is owned by CI, waiting on the host's approval, not by the Master

#### Scenario: The switch is off
- **WHEN** `deploy.requireApproval` is off, its default, and ABC-42 is `REVIEWED`
- **THEN** `deploy` is offered as before

#### Scenario: The server starter requires approval
- **WHEN** jagt starts from `jagt.server.yml.dist`
- **THEN** `deploy.requireApproval` is on
- **AND** from `jagt.desktop.yml.dist` or with no key it is off

### Requirement: The once-per-arrival guard survives a restart
`DeployOnApprovalJob` SHALL record the arrival it pressed on the task, keyed by the arrival's status stamp, before it presses.
A restart SHALL NOT press the same arrival again; a new arrival at `APPROVED` carries a new stamp and is pressed once.
#### Scenario: A refused arrival after a restart
- **WHEN** the deploy of an `APPROVED` ABC-42 was refused and jagt restarts with ABC-42 still in the same arrival
- **THEN** the job presses nothing for that arrival

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
