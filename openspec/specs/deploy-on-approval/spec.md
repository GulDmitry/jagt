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
