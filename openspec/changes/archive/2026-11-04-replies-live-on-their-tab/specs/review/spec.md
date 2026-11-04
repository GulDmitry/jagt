## MODIFIED Requirements

### Requirement: The replies report reads the file
`replies [task]` (`command/ReviewRepliesReport`) SHALL print every block off the file, never the badge, unrecognised shape
included. Its only button SHALL be the Drafted review tab, never the card (`GlobalCommand.aboutOneTask`).
A line typed at it SHALL be said (`AgentSessions.say` → `nudgeTaskWindow`), never relayed: a relay overwrites `task_context.md`.

#### Scenario: What will be posted
- **WHEN** you ask what will be posted
- **THEN** `replies <task>` or the Drafted review tab shows every draft, and the card has no replies button

#### Scenario: A drafted reply is wrong
- **WHEN** you say so under the open `replies` report
- **THEN** the session answers into the same report
