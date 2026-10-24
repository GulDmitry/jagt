## MODIFIED Requirements

### Requirement: The outcome is a field
`update_agent_status` SHALL take `outcome` (`question` | `no_changes` | `progress`) and `reviewRequestUrl`, the brief ending
all three at REVIEW_PENDING. What a report means SHALL be the task's `task/AgentReport`, never a marker read out of its
line; an outcome typed into the message is words. A `state.json` from before SHALL read it once from the line's marker.

#### Scenario: Clean round, nobody approved
- **WHEN** a round comes back clean, unapproved
- **THEN** REVIEWED, nothing asked of you

#### Scenario: An approval arrives
- **WHEN** an approval arrives
- **THEN** you are tapped, the one time: it is yours to deploy

#### Scenario: A marker typed into the message
- **WHEN** a session reports `outcome=question: which branch?` as its message and no `outcome`
- **THEN** the line reads as written, and nobody is asked

#### Scenario: A question from before the field
- **WHEN** jagt starts on a `state.json` whose line reads `awaiting: which database`
- **THEN** the card reads `NEEDS INPUT: which database`
