## MODIFIED Requirements

### Requirement: The unit of a round is a thread
`ReviewFacts.threads` SHALL carry every note, oldest first with its author; the agent answers the newest.
Threads SHALL be relayed while unresolved, the round telling how many notes are new or unchanged since the last round
(`RoundTrail.sinceLast`). The checks SHALL never decide whether a thread is answered. After a round with threads,
a hand-back to REVIEW_PENDING SHALL be refused until `review_replies.md` is written since that relay or a ship ran (`HandBackDue`).
#### Scenario: Red checks outside the code and a reviewer's answer
- **WHEN** ABC-42's session hands back a question about the checks and writes no block for the thread
- **THEN** refused, told how many threads await their block
#### Scenario: A resolved thread
- **WHEN** a reviewer resolves a thread on ABC-42's request
- **THEN** no later round reads it again
#### Scenario: GitHub review in the body
- **WHEN** a GitHub review is written in the body, not a thread
- **THEN** relayed all the same
#### Scenario: The agent answered last
- **WHEN** two polls read a thread whose newest note is by the account the host's tools act as, unless it opens with the AI line
- **THEN** neither relays it, as it waits on the reviewer; only a new note starts a round, and one with the AI line awaits an answer
