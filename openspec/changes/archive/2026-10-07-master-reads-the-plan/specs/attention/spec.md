## MODIFIED Requirements

### Requirement: Owner beyond the status
Beyond `Move.ownerOf`, a `REVIEW_PENDING` round changing nothing and drafting no reply SHALL wait on the reviewer,
one whose poll `AutoReviewWatch.stopped()` on the human. A plan or round the Master has not read
(`RoundState.masterReading`, `Move.masterReads`) SHALL be AGENT: `master review`, no badge.

#### Scenario: Master reading
- **WHEN** the plan or round is unread
- **THEN** the chip says `master review`
