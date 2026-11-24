## ADDED Requirements

### Requirement: The code reviewer reads a task's diff before its ship
The code reviewer SHALL read, once per tree state, the diff against its base of a task at REVIEW_PENDING holding no request.
It SHALL relay each finding to the session as a review round. `FlowRules` SHALL refuse SHIP while one awaits its block in `review_replies.md`.
A finding raised there SHALL not be posted again on the task's request.

#### Scenario: A session hands back
- **WHEN** ABC-42's session reports REVIEW_PENDING with its diff unread
- **THEN** each finding reaches the session as a review round, and SHIP is declined until its block is written

#### Scenario: A finding already raised
- **WHEN** ABC-42's request is read and a finding is one the pre-ship read raised
- **THEN** no note is posted for it
