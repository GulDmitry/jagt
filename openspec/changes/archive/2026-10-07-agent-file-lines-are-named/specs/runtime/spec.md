## ADDED Requirements

### Requirement: A line in the agent file names its gap
A REVIEW_PENDING SHALL be refused when the task added more than 3 lines since its base to the repository's agent
files (`AgentRuntime.projectAgentFiles`), or `task_notes.md` holds fewer `agent-file:` lines than it added
(`HandBack.notesOwed`). A count git cannot take refuses too, naming it.

#### Scenario: A worker adds two lines and names one
- **WHEN** a hand-back follows two added lines and one `agent-file:` line
- **THEN** it is refused, asking for one `agent-file:` line each

#### Scenario: A worker adds four lines
- **WHEN** a hand-back follows four added lines
- **THEN** it is refused, at most 3
