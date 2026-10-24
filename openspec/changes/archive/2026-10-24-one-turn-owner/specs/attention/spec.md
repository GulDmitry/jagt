## MODIFIED Requirements

### Requirement: A question is reported first
An agent MUST report `outcome=question` before the human sees the question (rule 1 of the brief's `context` step).
`AgentReport.QUESTION` SHALL make `Move.owner` whoever `Turns.next` names, from any status, and the `DashboardLine`
NEEDS INPUT. `YourMoveJob` SHALL ping once the projection turns a task YOU, or changes its advice while YOU, a report
or not. The move must have stood five seconds; a card already yours when jagt starts is not pinged.

#### Scenario: An agent stops to ask
- **WHEN** an agent asks
- **THEN** NEEDS INPUT, one desktop ping the first time
- **AND** where the Master answers it in `act`, the card shows the Master at work and nobody is pinged until it gives up

#### Scenario: The task contradicts the code
- **WHEN** it breaks a code guarantee
- **THEN** the agent asks before the code picks a side; "the ticket wins" is yours

#### Scenario: Settled unasked
- **WHEN** you `focus <task>`
- **THEN** an `OPEN QUESTIONS:` line ends its output

#### Scenario: A step the Master may not take
- **WHEN** its verdict leaves a plan or a round for you (`mine` names it)
- **THEN** you are pinged once; a step the Master presses within five seconds pings nobody

### Requirement: Owners after review
`REVIEWED`'s owner SHALL be the review request: nothing highlighted, no ping, `deploy` listed where no approval is
needed. Only `APPROVED` taps the human, and neither is theirs where the Master deploys it in `act`; `YourMoveJob`
MUST stay silent unless `Move` says YOU. `DEPLOYED`'s owner is nobody, as `DONE`'s; `done` stays highlighted.

#### Scenario: An approval lands
- **WHEN** `APPROVED`
- **THEN** you are pinged
