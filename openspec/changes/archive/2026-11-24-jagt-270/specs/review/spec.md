## MODIFIED Requirements

### Requirement: The reply shape is prescribed once
The round brief (`RoundBrief.of`), relayed every round, SHALL prescribe one block per thread: its link, one author-kind line
(`person`, `bot` or `reviewer`, given by jagt from the note's author), the quoted newest note, `FIXED | NO CHANGE | QUESTION`,
reply. Which threads a round carries and who answers them is [review-actors](../review-actors/spec.md).
#### Scenario: Replies too long
- **WHEN** replies run long
- **THEN** the agent broke its brief; a re-`sweep` re-briefs

## REMOVED Requirements

### Requirement: The unit of a round is a thread
**Reason**: Moved whole to the `review-actors` capability, with the answered-by-hand and agent-answered-last cells.
**Migration**: Read `openspec/specs/review-actors/spec.md`.

### Requirement: Threads are resolved at ship
**Reason**: Moved whole to the `review-actors` capability, where each posting cell names its ship.
**Migration**: Read `openspec/specs/review-actors/spec.md`.

### Requirement: Drafted replies are a file
**Reason**: Moved to the `review-actors` capability: which draft a ship posts, and who waits, is a cell of the matrix.
**Migration**: Read `openspec/specs/review-actors/spec.md`.
