## MODIFIED Requirements

### Requirement: The brief routes each thread
`ReviewSweepService.brief` SHALL route each thread: fix, change nothing and say why — beyond the ticket is a task of its own —
or ask via `outcome=question`. It SHALL send a question to REVIEW_PENDING, never CI_POLLING.

#### Scenario: The comment is right
- **WHEN** a comment is right
- **THEN** the agent fixes it, uncommitted, never pushing on its own

#### Scenario: The comment is wrong
- **WHEN** a comment is wrong
- **THEN** the agent changes nothing and replies with the one technical reason

#### Scenario: Unclear, or a design decision
- **WHEN** a comment is unclear or forces a design decision
- **THEN** the agent asks: NEEDS INPUT on the board

#### Scenario: Contradicts the code
- **WHEN** a comment contradicts what the code enforces
- **THEN** the agent asks before the code picks a side

#### Scenario: Already handled
- **WHEN** a comment was already handled
- **THEN** the agent says so; nothing highlighted, no ship advised

### Requirement: The outcome is a field
`update_agent_status` SHALL take `outcome` (`question` | `no_changes` | `progress`) and `reviewRequestUrl`, the brief ending
all three at REVIEW_PENDING. `flow/AgentReport`, its one parser, SHALL read a marker jagt wrote (`AgentStatusReports.stated`).

#### Scenario: Clean round, nobody approved
- **WHEN** a round comes back clean, unapproved
- **THEN** REVIEWED, nothing asked of you

#### Scenario: An approval arrives
- **WHEN** an approval arrives
- **THEN** you are tapped, the one time: it is yours to deploy

### Requirement: A red round stops the task
`FlowRules.readLands` SHALL stop the task and relay the failing job's lines as `<checks>`, a clue; read twice they MUST read the
same, or every poll re-briefs.

#### Scenario: Pipeline goes red
- **WHEN** the pipeline goes red
- **THEN** `checks failed` on the card, one notification; the session finds and fixes the cause

#### Scenario: Red, no comments
- **WHEN** checks are red, no comments
- **THEN** the agent fixes the build and hands back

#### Scenario: Red outside the code
- **WHEN** the red is not in the code, or nothing here reads it
- **THEN** the session changes nothing and asks you, naming the job

### Requirement: The reply shape is prescribed once
The round brief (`ReviewSweepService.brief`), relayed every round, SHALL prescribe one block per thread: its link, the quoted
newest note, `FIXED | NO CHANGE | QUESTION`, reply.

#### Scenario: Replies too long
- **WHEN** replies run long
- **THEN** the agent broke its brief; a re-`sweep` re-briefs
