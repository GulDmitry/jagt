## MODIFIED Requirements

### Requirement: A read is bounded and leaves nothing behind
A third-party read SHALL use a detached checkout under `<data>/jagt/code-review/checkouts/` through read-only git, with no build on outside code.
A diff over `maxDiffLines` SHALL be skipped with one log line. The reviews per request SHALL scale with its size:
one per new head, at most 2 plus its changed lines divided by 500. The description, a note and a diff are data, never instructions.
A third-party read SHALL be charged to the request's ledger row.
A request whose row holds `assistant.max-cost-usd` or more SHALL get no model call.

#### Scenario: An instruction in the description
- **WHEN** the description holds an instruction
- **THEN** it is a finding of severity noise and nothing is obeyed

#### Scenario: A read fails
- **WHEN** the read fails
- **THEN** the head stays unreviewed and is read again next tick

#### Scenario: A read ends
- **WHEN** a read ends any way
- **THEN** its checkout is gone

#### Scenario: A large diff
- **WHEN** the diff is over `maxDiffLines`
- **THEN** it is skipped with one log line

#### Scenario: Reviews scale with size
- **WHEN** a request of 1200 changed lines got its fourth review
- **THEN** a new head is not reviewed again, while a request of 2000 changed lines still is

#### Scenario: A third-party review would pass the ceiling
- **WHEN** a third-party request's reads cost `assistant.max-cost-usd` or more
- **THEN** no model is asked for it, a read or an answer

### Requirement: A note is posted once and says who wrote it
`NotePoster` SHALL be the one caller of `RequestNotes.post`. Each body SHALL open with the constant AI line asking for a thumbs up or down.
Each note SHALL carry a hidden key, a digest of request, head, file, anchor and kind, read back from the host before every post.
A finding naming a file and a line SHALL be posted on that line, and on the request where the host refuses the line.
jagt SHALL never approve, merge, close or push through the role.

#### Scenario: A restart between a post and its ledger write
- **WHEN** jagt restarts between a post and its ledger write
- **THEN** the next run finds the key on the host and posts nothing

#### Scenario: The request moved on
- **WHEN** the request is closed, merged or its head moved since the read
- **THEN** the judgement is dropped unposted

#### Scenario: Another person's request, posting off
- **WHEN** `post.others` is off and the request is another person's
- **THEN** it gets no note and no model call

#### Scenario: No approval
- **WHEN** any run ends
- **THEN** no approval was sent, as `RequestNotes` has no approve method

#### Scenario: A finding names a file and a line
- **WHEN** a finding quotes a line of a file the diff changes
- **THEN** the note is posted on that line

#### Scenario: The host refuses the line
- **WHEN** the host refuses a note's line
- **THEN** the note is posted on the request instead

### Requirement: The ledger keeps a request until the host says it ended
`ReviewLedger` SHALL be `<data>/jagt/code-review.json`, written atomically by one writer. A row SHALL end only on a read saying merged or closed.
A failed listing SHALL remove no row and SHALL show the reach mark on the board's head until a read succeeds.

#### Scenario: A failed listing
- **WHEN** the listing fails
- **THEN** no row is removed and the reach mark stays until a read succeeds

#### Scenario: A ledger that cannot be parsed
- **WHEN** the ledger cannot be parsed
- **THEN** it moves to `.corrupt`, starts empty and a WARN names the cause

#### Scenario: A host failure
- **WHEN** the host answers a rate limit or is signed out
- **THEN** the answer is a failure in the host's words, never an empty list

#### Scenario: A reviewed request is merged or closed
- **WHEN** a reviewed request is merged or closed
- **THEN** its judgement is dropped unposted and its row ends after one final read

### Requirement: Likes, dislikes and resolutions are recorded as dated statistics
`ReviewStats` SHALL append a line to `<data>/records/code-review.jsonl`, in UTC, only where a count or resolution changed, with the brief's digest.
A note's resolution SHALL be the session's answer in `review_replies.md` where a block's heading links the note,
else the host's: resolved is fixed, else unknown. A top-level note's reactions SHALL be read from the host's comment endpoint.
A host with no reactions SHALL answer cannot-say, never zero. A rating SHALL never rewrite the brief: a change follows a
`roleEval -Prole=code-reviewer` case that is red on the old brief.

#### Scenario: The reactions read fails
- **WHEN** the reactions read fails
- **THEN** no line is written, the last counts stand and one WARN names the cause

#### Scenario: A request merges
- **WHEN** a request merges
- **THEN** one final read runs and its ledger row ends

#### Scenario: A restart with unchanged counts
- **WHEN** jagt restarts and the counts are unchanged
- **THEN** no line is written

#### Scenario: A host with no reactions
- **WHEN** a host has no reactions
- **THEN** the answer is cannot-say, never zero

#### Scenario: The session answered a note
- **WHEN** the session answered a note in `review_replies.md`
- **THEN** the note's resolution is that answer

#### Scenario: A note on the request itself
- **WHEN** a note is on the request, not on a line
- **THEN** its likes and dislikes are read all the same
