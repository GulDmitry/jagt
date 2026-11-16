## MODIFIED Requirements

### Requirement: The auto-review poll only reads and drafts
The poll (`AutoReviewScheduler` → `ReviewSweepService`) SHALL only read and draft: threads relayed, the agent fixing locally into
`review_replies.md`, nothing posted without a human `ship`, the diff and drafts left for `code <alias>`. Code review is never fully automated.
Rounds: [review](../review/spec.md).

#### Scenario: Sweep by hand
- **WHEN** you type `review <task>`
- **THEN** it runs the sweep
- **AND** a round that answered every comment and changed no code is not your move: the open threads are the reviewer's
