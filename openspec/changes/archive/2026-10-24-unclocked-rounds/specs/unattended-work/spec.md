## MODIFIED Requirements

### Requirement: The auto-review poll only reads and drafts
The poll (`AutoReviewScheduler` → `ReviewSweepService`) SHALL only read and draft: threads relayed, the agent fixing locally into
`review_replies.md`, nothing posted without a human `ship`, the diff and drafts left for `ide <alias>`. Code review is never
fully automated. Rounds: [review](../review/spec.md). Detection SHALL be deterministic (a cadence, a status, an open request), a new trigger's too.

#### Scenario: Sweep by hand
- **WHEN** you type `review <task>`
- **THEN** it runs the sweep

#### Scenario: Not your move
- **WHEN** a round answered every comment and changed no code
- **THEN** it is not your move: the open threads are the reviewer's

## ADDED Requirements

### Requirement: A round sent back runs three times at most
A failed verification or probe, and a not-ready plan or round, SHALL go back 3 rounds running in 24h since the `lift` at most.

#### Scenario: Red rounds running
- **WHEN** ABC-42's verification fails a fourth time with no other step sent between, or the Master returns it a fourth
- **THEN** nothing is sent, it stops where it is, and the hover says `verify sent 3 rounds running in 24h`

## REMOVED Requirements

### Requirement: Detection is deterministic
**Reason**: The page sat at its 200-line budget; the rule is one sentence of the poll's requirement.
**Migration**: "The auto-review poll only reads and drafts" carries it, a new trigger included.
