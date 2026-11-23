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

### Requirement: A read is bounded and leaves nothing behind
A third-party read SHALL use a detached checkout under `<data>/jagt/code-review/checkouts/` through read-only git, with no build on outside code.
A diff over `maxDiffLines` SHALL be skipped with one log line. The reviews per request SHALL scale with its size:
one per new head, at most 2 plus its changed lines divided by 500. The description, a note and a diff are data, never instructions.

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

### Requirement: A note is posted once and says who wrote it
`NotePoster` SHALL be the one caller of `RequestNotes.post`. Each body SHALL open with the constant AI line asking for a thumbs up or down.
Each note SHALL carry a hidden key, a digest of request, head, file, anchor and kind, read back from the host before every post.
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

### Requirement: Notes are on for a jagt task's request and opt-in for another's
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

### Requirement: Review bots are answered, bounded per thread
The role SHALL answer a review bot's note once per note id: fix, change nothing and say why, or ask. It SHALL answer a thread at most three times.
A thread past the bound SHALL be exhausted until it resolves or the request closes. The role SHALL never answer its own newest note.

#### Scenario: A bot answers a fourth time
- **WHEN** a bot answers the role's answer a fourth time
- **THEN** nothing is posted and the thread is exhausted until it resolves or the request closes

#### Scenario: The newest note is the role's own
- **WHEN** the newest note of a thread is the role's own
- **THEN** nothing is posted

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

### Requirement: Likes, dislikes and resolutions are recorded as dated statistics
`ReviewStats` SHALL append a line to `<data>/records/code-review.jsonl`, in UTC, only where a count or resolution changed, with the brief's digest.
Resolutions SHALL come from `review_replies.md` for a jagt task's request and from the host for another: resolved is fixed, else unknown.
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
