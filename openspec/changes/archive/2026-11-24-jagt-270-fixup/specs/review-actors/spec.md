## MODIFIED Requirements

### Requirement: Every actor on a request has one outcome per cell
The actors are a person, a review bot, jagt's code reviewer, the session, the Master and the human answering by hand;
the moments are before the request exists, after the ship and on a later push. The cells below say who acts, what is posted
under which identity, what waits for the human and what says so. Each cell SHALL be pinned by a named test.

#### Scenario: A key a cell does not name
- **WHEN** a key is absent from a cell
- **THEN** it does not change that cell's outcome

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

### Requirement: The server starter replies to every note and counts the ratings
`jagt.server.yml.dist` SHALL set RPU to `true`. Every reply the Master ships SHALL open with the AI line asking for a thumbs rating.
The ratings of those replies SHALL be recorded in `records/code-review.jsonl` as replies, good or bad, beside the notes'.
A reply line carries no brief digest.

#### Scenario: S1 jagt runs from the server starter configuration
- **WHEN** jagt loads `jagt.server.yml.dist`
- **THEN** RPU is `true` and P3 holds for every note, a person's included
- **AND** the thumbs on those replies are recorded in the reviewer's statistics as replies, even where no ledger row was read first
