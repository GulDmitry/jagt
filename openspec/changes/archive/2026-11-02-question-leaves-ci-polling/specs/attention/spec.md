## MODIFIED Requirements

### Requirement: A question is reported first
An agent MUST write its question, options and pick in `task_notes.md`, report `outcome=question` and end its turn (rule 1 of the brief's `context`
step); its settings deny the CLI's question dialog. A question reported at CI_POLLING SHALL be refused, naming REVIEW_PENDING. `AgentReport.QUESTION`
SHALL make `Move.owner` whoever `Turns.next` names, from any status, and the `DashboardLine` NEEDS INPUT. `YourMoveJob` SHALL ping once the projection
turns a task YOU, or changes its advice while YOU, a report or not, after five seconds; not one already yours at start.

#### Scenario: An agent stops to ask
- **WHEN** an agent asks
- **THEN** NEEDS INPUT, one desktop ping the first time
- **AND** where the Master answers it in `act`, the card shows the Master at work and nobody is pinged until it gives up
- **WHEN** the task breaks a code guarantee
- **THEN** the agent asks before the code picks a side; "the ticket wins" is yours
- **WHEN** a session would ask with the CLI's question dialog, or ABC-42 stands at CI_POLLING and reports `outcome=question`
- **THEN** it is refused: the dialog, with question, options and pick in `task_notes.md` before the report; CI_POLLING, naming REVIEW_PENDING
- **WHEN** you `focus <task>`
- **THEN** an `OPEN QUESTIONS:` line ends its output

#### Scenario: A step the Master may not take
- **WHEN** its verdict leaves a plan or a round for you (`mine` names it)
- **THEN** you are pinged once; a step the Master presses within five seconds pings nobody
