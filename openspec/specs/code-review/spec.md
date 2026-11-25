# code-review Specification

## Purpose
A role on by default that reads a request's change, posts notes once, answers review bots and records the ratings its notes get.

## Requirements

### Requirement: The code reviewer reads a request's change once per head
The code reviewer SHALL read the diff, tests and description of an open request once per new head, on by default on both tracks.
It SHALL be a job with no region: it opens no task, moves none and shows nothing on the board. Its review SHALL gate no move.
A request by the acting account itself SHALL be skipped. Design: [`roles/code-reviewer/design.md`](../../../roles/code-reviewer/design.md).

#### Scenario: A jagt task's request gets a new head
- **WHEN** ABC-42's request gets a new head
- **THEN** the head is read once and its findings are posted as notes

#### Scenario: A request no task holds
- **WHEN** the host names a request no task holds
- **THEN** one ledger row is written and no task opens

#### Scenario: A request of the acting account
- **WHEN** the acting account itself authored the request
- **THEN** it is skipped

#### Scenario: The role is off or the host is none
- **WHEN** `roles.code-reviewer.enabled` is `false` or the code host is none
- **THEN** nothing is listed, read or posted, and no failure is shown

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

### Requirement: A jagt task's request gets no note and another's is opt-in
On a jagt task's request notes SHALL be on by default (`post.own`); on another person's request only with `post.others`, off by default.
The settings SHALL live in `roles/code-reviewer/review.yml`, unset read as on, never in `jagt.yml`.

#### Scenario: A jagt task's request, defaults
- **WHEN** `review.yml` is absent and a jagt task's request gets a new head
- **THEN** its findings are posted as notes

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

### Requirement: The session hears the role and the Master yields code review to it
A note opening with the AI line SHALL await an answer whoever posted it, so the session's round relays it once.
While the role is on and the project has a code host, the Master's brief row naming it in `unless` SHALL be skipped.
With the role off or no code host the Master SHALL read all three rows.

#### Scenario: A note with the AI line is newest
- **WHEN** the newest note of a thread opens with the AI line
- **THEN** the round relays it once

#### Scenario: The role is off
- **WHEN** the role is off or the project has no code host
- **THEN** the Master reads all three rows

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
