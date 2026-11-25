## MODIFIED Requirements

### Requirement: The auto-review poll only reads and drafts
The poll (`AutoReviewScheduler` → `ReviewSweepService`) SHALL only read and draft: threads relayed, the agent fixing locally into
`review_replies.md`, the diff and drafts left for `code <alias>`. A reply reaches the host only by a ship: the human's, or the Master's
in `act` where `codeReview.repliesToPeopleUnasked` is `true`, as the server starter sets it (P3 and S1 of [review-actors](../review-actors/spec.md)).
Rounds: [review](../review/spec.md).

#### Scenario: Sweep by hand
- **WHEN** you type `review <task>`
- **THEN** it runs the sweep
- **AND** a round that answered every comment and changed no code is not your move: the open threads are the reviewer's
