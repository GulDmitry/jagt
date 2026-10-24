## MODIFIED Requirements

### Requirement: The unit of a round is a thread
`ReviewFacts.threads` SHALL carry every note, oldest first with its author; the agent answers the newest one, and a thread whose
newest note is its own waits on the reviewer. Threads SHALL be relayed while unresolved; a resolved one is never read again.

#### Scenario: GitHub review in the body
- **WHEN** a GitHub review is written in the body, not a thread
- **THEN** relayed all the same

#### Scenario: The agent answered last
- **WHEN** two polls read a thread whose newest note is by the account the host's tools act as
- **THEN** neither relays it, the task is not REVIEWED, and only a new note on it starts a round
