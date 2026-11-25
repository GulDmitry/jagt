## ADDED Requirements

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
