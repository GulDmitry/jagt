## MODIFIED Requirements

### Requirement: A person's note is drafted, and a draft is posted by one cell
A person's note SHALL be relayed and drafted by the session into `review_replies.md`.
Each block SHALL carry its author kind: person, bot, reviewer; unknown where the notes were unread. Where a ship posts it, the reply goes under
the acting account, opening with the AI line whenever PRR is `true`, whatever RPU. The Master SHALL ship a person's draft only where RPU is `true`.
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

#### Scenario: P6 A reply is posted whatever RPU
- **WHEN** a ship posts a bot's draft and RPU is `false`
- **THEN** the reply opens with the AI line
