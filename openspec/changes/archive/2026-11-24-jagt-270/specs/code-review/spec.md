## RENAMED Requirements

- FROM: `### Requirement: Notes are on for a jagt task's request and opt-in for another's`
- TO: `### Requirement: A jagt task's request gets no note and another's is opt-in
The code reviewer SHALL never post on a jagt task's request: its findings reach the session before the ship, a failed read's
are relayed (C1-C3 of [review-actors](../review-actors/spec.md)). On another person's request it SHALL post only with
`post.others`, off by default.
The settings SHALL live in `roles/code-reviewer/review.yml`, unset read as on, never in `jagt.yml`.

#### Scenario: A jagt task's request, defaults
- **WHEN** `review.yml` is absent and a jagt task's request gets a new head
- **THEN** no note is posted on it

### Requirement: Review bots are answered, bounded per thread
On a request no jagt task holds the role SHALL answer a review bot's note once per note id: fix, change nothing and say why, or ask.
On a jagt task's request the session alone answers, never the role. It SHALL answer a thread at most three times.
A thread past the bound SHALL be exhausted until it resolves or the request closes. The role SHALL never answer its own newest note.

#### Scenario: A bot answers a fourth time
- **WHEN** a bot answers the role's answer a fourth time
- **THEN** nothing is posted and the thread is exhausted until it resolves or the request closes

#### Scenario: The newest note is the role's own
- **WHEN** the newest note of a thread is the role's own
- **THEN** nothing is posted

### Requirement: The session hears the role and the Master yields code review to it
A note that opens a thread with the AI line SHALL await an answer whoever posted it, so the session's round relays it once;
a reply jagt posted, the AI line or not, SHALL not.
While the role is on and the project has a code host, the Master's brief row naming it in `unless` SHALL be skipped.
With the role off or no code host the Master SHALL read all three rows.

#### Scenario: A note with the AI line is newest
- **WHEN** a thread's opening note begins with the AI line
- **THEN** the round relays it once

#### Scenario: The role is off
- **WHEN** the role is off or the project has no code host
- **THEN** the Master reads all three rows

### Requirement: Likes, dislikes and resolutions are recorded as dated statistics per change
`ReviewStats` SHALL append a line to `<data>/records/code-review.jsonl`, in UTC, only where a count or resolution changed, with the brief's digest.
A note's resolution SHALL be the session's answer in `review_replies.md` where a block's heading links the note,
else the host's: resolved is fixed, else unknown.
The thumbs on every reply opening with the AI line, the Master's shipped replies included, SHALL be recorded as replies, good or bad.

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

### Requirement: The code reviewer reads a task's diff before its ship
The code reviewer SHALL read the diff of a task at REVIEW_PENDING once per tree state, with or without a request, relaying each
finding as a round. `FlowRules` SHALL refuse SHIP, the Master's too, while a finding awaits its block in `review_replies.md`
or the tree is unread. A finding raised there SHALL never be posted on the request. A failed read, a reviewer switched off
or an empty diff SHALL never hold a SHIP; a failed read's findings are relayed later.

#### Scenario: A session hands back
- **WHEN** ABC-42's session reports REVIEW_PENDING and the Master presses SHIP before its diff was read
- **THEN** SHIP is declined, and once read each finding reaches the session as a review round that holds SHIP until its block is written

#### Scenario: A finding already raised
- **WHEN** ABC-42's request is read and a finding is one the pre-ship read raised
- **THEN** no note is posted for it, nor for any finding, on a jagt task's request
