# review-actors Specification

## Purpose
Who acts, what is posted under which identity and what waits for the human, for each actor on a request, moment and configuration.

## Requirements

### Requirement: Every actor on a request has one outcome per cell
The actors are a person, a review bot, jagt's code reviewer, the session, the Master and the human answering by hand;
the moments are before the request exists, after the ship and on a later push. The cells below say who acts, what is posted
under which identity, what waits for the human and what says so. Each cell SHALL be pinned by a named test.

#### Scenario: A key a cell does not name
- **WHEN** a key is absent from a cell
- **THEN** it does not change that cell's outcome

### Requirement: The keys have fixed short names
The cells SHALL name the keys as follows.
Keys: PRR `postReviewReplies`, RRA `reviewReplyAuthors`, RPU `codeReview.repliesToPeopleUnasked` (default `false`),
SPED `shipPostsEveryDraft`, `post.others`, `roles.master.mine`, the Master's mode (`act`, `judge`, `off`).
The acting account is the host CLI's login. Rounds: [review](../review/spec.md). Reviewer: [code-review](../code-review/spec.md).

#### Scenario: A key is named
- **WHEN** a scenario says RPU
- **THEN** it means `codeReview.repliesToPeopleUnasked`

### Requirement: A finding before the request goes to the session
Before the task has a request the code reviewer SHALL read the tree once per state and relay each finding as a round. A relayed
finding is no draft: it holds no one, and no ship posts it. A failed read SHALL hold nothing.

#### Scenario: C1 The session hands back and findings exist
- **WHEN** ABC-42's session reports REVIEW_PENDING and the code reviewer reads findings
- **THEN** each is relayed as a round headed `reviewer:`, SHIP is refused to the human and the Master until each has its block
- **AND** nothing is posted, and those blocks are no draft: they hold nobody, no ship posts them

#### Scenario: C2 The read fails, the reviewer is off or the diff is empty
- **WHEN** the pre-ship read fails, `roles.code-reviewer.enabled` is `false` or the diff is empty
- **THEN** SHIP is not held, and the ship goes on
- **AND** the findings of a tree whose read failed, read later, are relayed to the session, never posted on the request

### Requirement: A jagt task's request never gets the reviewer's note first
After the ship and on every later push the code reviewer SHALL read the tree before it is pushed, as C1 and C2.
It SHALL never post on a jagt task's request. On a third-party request it SHALL post once per head only with `post.others`.

#### Scenario: C3 A task with a request hands back a new tree
- **WHEN** ABC-42 has a request and its session hands back a new tree
- **THEN** the tree is read before the push as C1 and C2
- **AND** the reviewer posts nothing on ABC-42's request, whether the head is new or the ship just ran

#### Scenario: C4 A third-party request gets a new head
- **WHEN** a request no task holds gets a new head and `post.others` is on
- **THEN** its notes are posted once per head under the acting account, each opening with the AI line and carrying the hidden key
- **WHEN** `post.others` is off
- **THEN** the request is not read and no model is called

### Requirement: A person's note is drafted, and a draft is posted by one cell
A person's note SHALL be relayed and drafted by the session into `review_replies.md`.
Each block SHALL carry its author kind: person, bot, reviewer; unknown where the notes were unread. Where a ship posts it, the reply goes under
the acting account, opening with the AI line where RPU and PRR are `true`. The Master SHALL ship a person's draft only where RPU is `true`.
A ship SHALL never post a reviewer's or an unknown block.

#### Scenario: P1 Replies are off
- **WHEN** a person's note is relayed and PRR is `false`
- **THEN** the session drafts, no ship posts, and the Master in `act` ships nothing
- **AND** your-move says drafted replies wait

#### Scenario: P2 A person's draft, the Master unasked
- **WHEN** a person's note is drafted and RPU is `false`, the laptop default, whatever RRA
- **THEN** the Master ships nothing, the code included, and your-move says drafts wait
- **AND** the human's ship posts it under the acting account where RRA is empty or matches the author
- **AND** else it stays announced for the human to post by hand

#### Scenario: P3 The Master replies to a person
- **WHEN** RPU and PRR are `true`, RRA is empty or matches, the Master is in `act` and `mine` holds neither `ship` nor `reply`
- **THEN** the Master ships, each reply is posted under the acting account opening with the AI line
- **AND** no reply is relayed back to the session as a note awaiting an answer

#### Scenario: P4 The reply is kept for the human
- **WHEN** as P3 but `mine: [reply]`
- **THEN** the Master ships the code, the session leaves `review_replies.md`, and your-move says drafts wait

#### Scenario: P5 The ship is kept, or the Master is not in act
- **WHEN** `mine: [ship]`, or the Master is in `judge` or `off`
- **THEN** nothing is pressed and the verdict is written
- **AND** the human's ship follows P1 and P2

### Requirement: A bot is answered once, by one actor
On a jagt task's request a bot's note SHALL be answered by the session alone. On a third-party request the code reviewer SHALL
answer it. It answers once per note id and three times per thread at most, each answer opening with the AI line.

#### Scenario: B1 A bot's draft on a jagt task's request
- **WHEN** only a bot's note is drafted on ABC-42's request and a ship posts it (SPED, or RRA matches the bot)
- **THEN** the Master in `act` ships it whatever RPU, and the code reviewer does not answer the bot
- **AND** a person's block in the same file makes it P2 under SPED, and it stays announced where RRA names only the bot

#### Scenario: B2 A bot writes on a third-party request
- **WHEN** a bot writes on a request no task holds and `post.others` is on
- **THEN** the code reviewer answers once per note, three times per thread at most, each opening with the AI line
- **WHEN** `post.others` is off
- **THEN** nothing is answered

### Requirement: The unit of a round is a thread
`ReviewFacts.threads` SHALL carry every note since your answer, oldest first with its author; the agent answers the newest.
Threads SHALL be relayed while unresolved, the round telling how many notes are new or unchanged since the last round
(`RoundTrail.sinceLast`). The checks SHALL never decide whether a thread is answered. After a round with threads,
a hand-back to REVIEW_PENDING SHALL be refused until `review_replies.md` is written since that relay or a ship ran (`HandBackDue`).

#### Scenario: Red checks outside the code and a reviewer's answer
- **WHEN** ABC-42's session hands back a question about the checks and writes no block for the thread
- **THEN** refused, told how many threads await their block

#### Scenario: A resolved thread, a review in the body
- **WHEN** a reviewer resolves a thread on ABC-42's request
- **THEN** no later round reads it again
- **WHEN** a GitHub review is written in the body, not a thread
- **THEN** relayed all the same

### Requirement: A thread the human or jagt answered waits on the reviewer
A thread SHALL wait on the reviewer after the human answered it by hand or posted a draft. A thread whose newest note is a reply
jagt posted, with the AI line or not, SHALL never be relayed. Only a thread-opening note that begins with the AI line awaits an answer.

#### Scenario: H1 The human answers by hand or posts a draft
- **WHEN** you answer ABC-42's thread by hand or post a draft, and the reviewer writes again
- **THEN** the next round relays the thread's link and the reviewer's new note only, not the notes before your answer

#### Scenario: H2 The newest note is a reply jagt posted
- **WHEN** two polls read a thread whose newest note is a reply jagt posted under the acting account, AI line or not
- **THEN** neither relays it, and only a new note starts a round
- **WHEN** a note that opens the thread begins with the AI line
- **THEN** the round relays it once

### Requirement: The server starter replies to every note and counts the ratings
`jagt.server.yml.dist` SHALL set RPU to `true`. Every reply the Master ships SHALL open with the AI line asking for a thumbs rating.
The ratings of those replies SHALL be recorded in `records/code-review.jsonl` as replies, good or bad, beside the notes'.
A reply line carries no brief digest.

#### Scenario: S1 jagt runs from the server starter configuration
- **WHEN** jagt loads `jagt.server.yml.dist`
- **THEN** RPU is `true` and P3 holds for every note, a person's included
- **AND** the thumbs on those replies are recorded in the reviewer's statistics as replies, even where no ledger row was read first

### Requirement: Threads are resolved at ship
The agent SHALL resolve at ship time over its own MCP (`ShipService.repliesStep`), only threads whose code it changed. During the
round it SHALL post nothing, and a draft SHALL go only where the answered note's author matches `reviewReplyAuthors`.
The ship brief SHALL name what is posted per author kind: bot and person blocks as the cells say, reviewer and unknown never.

#### Scenario: Replies are posted
- **WHEN** you `ship` and a cell above says the draft is posted
- **THEN** replies are posted only now; a fixed thread is resolved, a disputed one left for the reviewer

#### Scenario: A ship meets blocks of every kind
- **WHEN** you `ship` and `review_replies.md` holds reviewer, unknown, person and bot blocks
- **THEN** the brief says to post no reviewer's or unknown block
- **AND** a person's block only where RPU is `true` or a human ships

### Requirement: Drafted replies are a file
`TaskViews` SHALL stat `review_replies.md` for presence, never a count; `ReviewDrafts.pending` SHALL want it newer than
`mrCreatedAt`. jagt SHALL never delete the file; the agent is asked to.

#### Scenario: Stale reply file
- **WHEN** the file predates `mrCreatedAt`
- **THEN** nothing is pending

### Requirement: A draft is spent by a ship that posts it
`CodeReviewConfig.shipPostsEveryDraft` SHALL decide when drafts are spent: under `postReviewReplies=false` or a partial
`reviewReplyAuthors` filter none is. The announcement stands, whatever the status, until you post or end them.
Your-move SHALL say drafted replies wait, whatever the round reported, and name a ship as posting them only where a cell says so.

#### Scenario: Drafts a ship left
- **WHEN** you `ship` under `postReviewReplies=false`, or a ship leaves drafts to a person's note unposted and the task leaves the hand-back
- **THEN** the announcement stands: the card still shows them pending

#### Scenario: No code changed
- **WHEN** a round reports `NO_CHANGES` and drafted replies wait
- **THEN** the your-move notification says drafted replies wait, and a ship posts them only where a cell says so
