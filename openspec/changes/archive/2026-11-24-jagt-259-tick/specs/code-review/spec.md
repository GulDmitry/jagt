## MODIFIED Requirements

### Requirement: The code reviewer reads a task's diff before its ship
The code reviewer SHALL read the diff of a task at REVIEW_PENDING holding no request once per tree state, relaying each finding as a round.
`FlowRules` SHALL refuse SHIP, the Master's too, while a finding awaits its block in `review_replies.md` or the tree is unread.
A finding raised there SHALL not be posted again on the request. A failed read, a reviewer switched off or an empty diff SHALL never hold a SHIP.

#### Scenario: A session hands back
- **WHEN** ABC-42's session reports REVIEW_PENDING and the Master presses SHIP before its diff was read
- **THEN** SHIP is declined, and once read each finding reaches the session as a review round that holds SHIP until its block is written

#### Scenario: A finding already raised
- **WHEN** ABC-42's request is read and a finding is one the pre-ship read raised
- **THEN** no note is posted for it
