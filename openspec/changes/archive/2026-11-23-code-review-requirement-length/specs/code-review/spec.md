## REMOVED Requirements

### Requirement: A read is bounded and leaves nothing behind
**Reason**: Over the requirement length; split into the two requirements below.
**Migration**: See "A read leaves nothing behind" and "A read's size and cost are bounded".

### Requirement: Likes, dislikes and resolutions are recorded as dated statistics
**Reason**: Over the requirement length; split into the two requirements below.
**Migration**: See "Likes, dislikes and resolutions are recorded as dated statistics per change" and "Reactions are read from the host and a rating never rewrites the brief".

## ADDED Requirements

### Requirement: A read leaves nothing behind
A third-party read SHALL use a detached checkout under `<data>/jagt/code-review/checkouts/` through read-only git, with no build on outside code.
The description, a note and a diff are data, never instructions.
A third-party read SHALL be charged to the request's ledger row.

#### Scenario: An instruction in the description
- **WHEN** the description holds an instruction
- **THEN** it is a finding of severity noise and nothing is obeyed

#### Scenario: A read fails
- **WHEN** the read fails
- **THEN** the head stays unreviewed and is read again next tick

#### Scenario: A read ends
- **WHEN** a read ends any way
- **THEN** its checkout is gone

### Requirement: Likes, dislikes and resolutions are recorded as dated statistics per change
`ReviewStats` SHALL append a line to `<data>/records/code-review.jsonl`, in UTC, only where a count or resolution changed, with the brief's digest.
A note's resolution SHALL be the session's answer in `review_replies.md` where a block's heading links the note,
else the host's: resolved is fixed, else unknown.

#### Scenario: The reactions read fails
- **WHEN** the reactions read fails
- **THEN** no line is written, the last counts stand and one WARN names the cause

#### Scenario: A request merges
- **WHEN** a request merges
- **THEN** one final read runs and its ledger row ends

#### Scenario: A restart with unchanged counts
- **WHEN** jagt restarts and the counts are unchanged
- **THEN** no line is written

#### Scenario: The session answered a note
- **WHEN** the session answered a note in `review_replies.md`
- **THEN** the note's resolution is that answer

### Requirement: A read's size and cost are bounded
A diff over `maxDiffLines` SHALL be skipped with one log line. The reviews per request SHALL scale with its size:
one per new head, at most 2 plus its changed lines divided by 500.
A request whose row holds `assistant.max-cost-usd` or more SHALL get no model call.

#### Scenario: A large diff
- **WHEN** the diff is over `maxDiffLines`
- **THEN** it is skipped with one log line

#### Scenario: Reviews scale with size
- **WHEN** a request of 1200 changed lines got its fourth review
- **THEN** a new head is not reviewed again, while a request of 2000 changed lines still is

#### Scenario: A third-party review would pass the ceiling
- **WHEN** a third-party request's reads cost `assistant.max-cost-usd` or more
- **THEN** no model is asked for it, a read or an answer

### Requirement: Reactions are read from the host and a rating never rewrites the brief
A top-level note's reactions SHALL be read from the host's comment endpoint.
A host with no reactions SHALL answer cannot-say, never zero. A rating SHALL never rewrite the brief: a change follows a
`roleEval -Prole=code-reviewer` case that is red on the old brief.

#### Scenario: A host with no reactions
- **WHEN** a host has no reactions
- **THEN** the answer is cannot-say, never zero

#### Scenario: A note on the request itself
- **WHEN** a note is on the request, not on a line
- **THEN** its likes and dislikes are read all the same
