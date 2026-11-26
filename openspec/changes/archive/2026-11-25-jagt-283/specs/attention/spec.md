## MODIFIED Requirements

### Requirement: A review round or an unread plan waits on the agent or the reviewer
Beyond `Move.ownerOf`, a `REVIEW_PENDING` round changing nothing and drafting no reply SHALL wait on the reviewer, one whose poll
`AutoReviewWatch.stopped()` on the human. A plan or round the Master has not read (`RoundState.arbiterReading`, `FlowRules.readByTheArbiter`) SHALL be
AGENT: `master review`, no badge.
#### Scenario: An unread plan or round
- **WHEN** the plan or round is unread
- **THEN** the chip says `master review`
