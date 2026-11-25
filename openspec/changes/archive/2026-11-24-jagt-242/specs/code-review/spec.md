## MODIFIED Requirements

### Requirement: A note is posted once and says who wrote it
`NotePoster` SHALL be the one caller of `RequestNotes.post`.
Each body SHALL open with the constant AI line, then the defect, its evidence as file:line and the fix.
Each note SHALL carry a hidden key, read back before a post.
A finding SHALL go on its diff line; with none, or if the host refuses it, one WARN names the cause and no note is posted.
A finding an open thread of the role's own says SHALL post nothing.
jagt SHALL never approve, merge, close or push through the role.

#### Scenario: A note is posted
- **WHEN** the role posts a note
- **THEN** it opens with `AI note: 👍 helps, 👎 does not; reply with what is wrong.`
- **AND** its text is at most three lines and 60 words naming file:line and the fix

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
- **THEN** the note is posted on that line, its position sent as JSON where the host needs it

#### Scenario: The host refuses the line
- **WHEN** the host refuses a note's line
- **THEN** no note is posted and one WARN names the cause

#### Scenario: A finding has no diff line
- **WHEN** a finding names no line of the diff
- **THEN** no note is posted and one WARN names the cause

#### Scenario: A re-review after a new push
- **WHEN** a finding is what an open thread of the role's own already says
- **THEN** nothing new is posted

### Requirement: Likes, dislikes and resolutions are recorded as dated statistics per change
`ReviewStats` SHALL append a line to `<data>/records/code-review.jsonl`, in UTC,
only where a count, resolution or reason changed, with the brief's digest.
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

## ADDED Requirements

### Requirement: A rating's reason is what a human replied
A note's reason SHALL be the human replies under it with their reactions; a thumbs down with no reply SHALL be reason unknown.

#### Scenario: A human replies under a note
- **WHEN** a human replies under the role's note
- **THEN** the reply and its reactions are recorded with the note's rating as its reason

#### Scenario: A thumbs down with no reply
- **WHEN** a note is thumbed down and no human replied
- **THEN** its reason is recorded as unknown

### Requirement: A note with the earlier AI line still counts
A note opening with `AI review note: react with a thumbs up if it helps, a thumbs down if it does not.` SHALL count as the role's.

#### Scenario: A note with the earlier AI line
- **WHEN** a note posted with the earlier AI line is read
- **THEN** it still counts as the role's note

## REMOVED Requirements

### Requirement: Review bots are answered, bounded per thread
**Reason**: Moved whole to the `review` capability, where a round is answered.
**Migration**: Read `openspec/specs/review/spec.md`.

### Requirement: The code reviewer reads a task's diff before its ship
**Reason**: Moved whole to the `review` capability, where a round is answered.
**Migration**: Read `openspec/specs/review/spec.md`.
