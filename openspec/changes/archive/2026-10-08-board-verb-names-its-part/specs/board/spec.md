## MODIFIED Requirements

### Requirement: Commands are two declarations
A verb a task owns SHALL be a `flow/TaskAction` row gated by `Move`, run by `CommandService`; one no task owns is a
`command/GlobalCommand` bean (`command/*`, collected by `GlobalCommands`: id, hint, usage, report or not, about one task
or not, the board part it opens typed alone) running itself. `CommandReference` renders both: `help`'s text and the
palette's verb list. A launch that creates no task is refused.

#### Scenario: Reports
- **WHEN** the human runs `stats`, `master` or `finished`
- **THEN** `stats` gives per task the time on the human, the agent, the code host
- **AND** `master` what the Master judged, and where the human did otherwise: passed then reverted or never deployed,
  failed then deployed
- **AND** `finished` every retired task with its status log

#### Scenario: A verb typed alone
- **WHEN** the human types `do` alone
- **THEN** the launch row takes the focus
