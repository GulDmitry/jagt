## MODIFIED Requirements

### Requirement: A question is reported first
An agent MUST report `outcome=question` before the human sees the question, rule 1 of `sub-agent-context.md` and
`update_agent_status`. `AgentReport.QUESTION` SHALL make `Move.owner` YOU from any status, the
`DashboardLine` NEEDS INPUT, and one ping from `YourMoveJob` on the transition into asking. That job SHALL ping
whenever the projection turns a task YOU, or changes its advice while it stays YOU. A step that becomes yours
without any report is pinged too; a card already yours when jagt starts is not.

#### Scenario: An agent stops to ask
- **WHEN** an agent asks
- **THEN** NEEDS INPUT, one desktop ping the first time

#### Scenario: The task contradicts the code
- **WHEN** it breaks a code guarantee
- **THEN** the agent asks before the code picks a side; "the ticket wins" is yours

#### Scenario: Settled unasked
- **WHEN** you `focus <task>`
- **THEN** an `OPEN QUESTIONS:` line ends its output

#### Scenario: A step the Master may not take
- **WHEN** its verdict leaves a plan or a round for you (`mine` names it)
- **THEN** you are pinged once

### Requirement: Owners after review
`REVIEWED`'s owner SHALL be the review request: nothing highlighted, no ping, `deploy` listed where no approval is
needed. Only `APPROVED` taps the human; `YourMoveJob` MUST stay silent unless `Move` says
YOU. `DEPLOYED`'s owner is nobody, as `DONE`'s; `done` stays highlighted.

#### Scenario: An approval lands
- **WHEN** `APPROVED`
- **THEN** you are pinged
