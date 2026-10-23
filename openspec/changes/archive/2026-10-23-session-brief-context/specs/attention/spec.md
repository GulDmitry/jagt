## MODIFIED Requirements

### Requirement: A question is reported first
An agent MUST report `outcome=question` before the human sees the question (rule 1 of the brief's `context` step).
`AgentReport.QUESTION` SHALL make `Move.owner` YOU from any status and the `DashboardLine` NEEDS INPUT.
`YourMoveJob` SHALL ping once the projection turns a task YOU, or changes its advice while YOU, a report or not.
The move must have stood five seconds; a card already yours when jagt starts is not pinged.

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
- **THEN** you are pinged once; a step the Master presses within five seconds pings nobody
