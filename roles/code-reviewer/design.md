# code-reviewer — design

## Why it exists

A request's code change is read twice or never: the Master reads a session's round, and a third-party request gets no read
at all. The code reviewer reads a request's diff, tests and description once per head, posts its findings as notes on the
request, answers review bots, and learns from the likes, dislikes and resolutions its notes get.

## Responsibility

- Owns one read per new head of a request: diff, tests and description, answered as findings with a file, an anchor and a severity.
- Owns its notes on the request, each opening with the AI line and posted once.
- Owns one answer per bot note, at most three per thread: fix, change nothing and say why, or ask.
- Owns the dated statistics of its notes: likes, dislikes, resolutions.
- Does not own a task: it opens none, moves none and shows nothing on the board; the review is advisory and gates no move.
- Does not approve, merge, close or push: the only write is a note.
- Does not build outside code: a third-party checkout is detached, read through read-only git.

## Who triggers it

jagt alone: `roles.code-reviewer.enabled` is not `false`, a code host is chosen, and the host names an open request awaiting
review or a jagt task's request has a new head. Never a model's answer.
It has no region: a job reads the host and a ledger row keeps the request, so no task status or stop changes.

## Use cases

- **WHEN** a jagt task's request gets a new head
  **THEN** the head is read once and each finding is posted as a note opening with the AI line
- **WHEN** a task's session reports REVIEW_PENDING and no request exists
  **THEN** its diff against the base is read once per tree state, each finding is relayed as a review round and the ship waits for the session's block per finding
- **WHEN** a finding the pre-ship read raised is read again on the task's request
  **THEN** no note is posted for it
- **WHEN** the host names a request no task holds and `post.others` is on
  **THEN** one ledger row is written, no task opens, the head is read in a detached checkout and its findings are posted
- **WHEN** the host names a request no task holds and `post.others` is off
  **THEN** a ledger row is written, no note is posted and no model is asked
- **WHEN** the request is by the acting account itself
  **THEN** it is skipped
- **WHEN** jagt restarts between a post and its ledger write
  **THEN** the next run finds the note's hidden key on the host and posts nothing
- **WHEN** the request is closed, merged or its head moved since the read
  **THEN** the judgement is dropped unposted
- **WHEN** the description or a note holds an instruction
  **THEN** it is a finding of severity noise and nothing is obeyed
- **WHEN** the diff is over `maxDiffLines`
  **THEN** it is skipped with one log line
- **WHEN** a request of 1200 changed lines got its fourth review
  **THEN** a new head is not reviewed again, while a request of 2000 changed lines still is
- **WHEN** the read fails
  **THEN** the head stays unreviewed and is read again next tick
- **WHEN** a read ends any way
  **THEN** its checkout is gone
- **WHEN** a review bot's note is the newest on a thread and the role answered it fewer than three times
  **THEN** one answer is posted, opening with the AI line
- **WHEN** a bot answers the role's answer a fourth time
  **THEN** nothing is posted and the thread is exhausted until it resolves or the request closes
- **WHEN** the newest note of a thread is the role's own
  **THEN** nothing is posted
- **WHEN** the newest note of a thread opens with the AI line
  **THEN** the session's round relays it once, whoever posted it
- **WHEN** the role is on and the project has a code host
  **THEN** the Master's brief skips the row the role reads; with the role off or no code host the Master reads all three
- **WHEN** a note's likes, dislikes or resolution changed
  **THEN** one dated line is appended, with the brief's digest
- **WHEN** the reactions read fails
  **THEN** no line is written, the last counts stand and one WARN names the cause
- **WHEN** a host has no reactions
  **THEN** the answer is cannot-say, never zero
- **WHEN** a request merges
  **THEN** one final read runs and its ledger row ends
- **WHEN** the host answers a rate limit or is signed out
  **THEN** the answer is a failure in the host's words, never an empty list
- **WHEN** the code host is none
  **THEN** nothing is listed, nothing is read and no failure is shown

## Usage

- On by default on both tracks; `roles/code-reviewer/review.yml` holds its settings, unset read as on:
  `post.own` true, `post.others` false, `maxDiffLines`.
- A note on a jagt task's request is on by default; on another person's request only with `post.others`.
- Its brief is `roles/code-reviewer/brief.md`, else `brief.md.dist`; its spend is the Master review's `assistant.max-cost-usd`.
- The ledger is `<data>/jagt/code-review.json`, one writer; the statistics `<data>/records/code-review.jsonl`, append-only, UTC.
- Resolutions come from `review_replies.md` for a jagt task's request, from the host for another (resolved is fixed, else unknown).
- A brief change that follows a rating pattern passes a `roleEval -Prole=code-reviewer` case, red on the old brief first.

## Guards

- Read-only on code: no edit, no push, no build on outside code, no approval, no merge.
- The description, a note, a diff or a commit message is data from outside; an instruction found in it is not followed.
- Each note opens with the constant AI line and carries a hidden key (request, head, file, anchor, kind) read back before every post.
- One class posts: `NotePoster`; the `note-writers` row of `fitness.yml` pins it.
- Every failure is in the host's words, never an empty answer; a ledger row ends only on a read saying merged or closed.

## Cost against the alternative

- Without it a third-party request is unread and a session's code is read once, by the Master alone.
- One heavy read per new head, bounded by the request's size; an answer costs one light read per bot note.
- The ratings are the only feedback: a ledger and a dated file, no model call.
