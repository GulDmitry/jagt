## MODIFIED Requirements

### Requirement: An open request is what the poller watches
`AutoReviewCadence.polls` SHALL watch an open request, never a status: an unmerged one exists and the task is not DONE. At `REVIEWED`, `APPROVED` or
`DEPLOYED` only a reviewer moves it on: it is read every `maxIntervalMinutes`, and past its window up to `waitingMaxIntervalMinutes` (60).

#### Scenario: Comments after hand-back
- **WHEN** comments arrive after the agent handed back
- **THEN** the next poll picks them up
- **AND** threads the reviewer never resolves keep polls going; the agent is re-briefed only when the round changed

#### Scenario: Deployed, green, unapproved
- **WHEN** a deployed task comes back green and unapproved
- **THEN** it stays `deployed`, read every `maxIntervalMinutes` from the start: a poll never moves work that went out

### Requirement: The polling window is per round
The window SHALL run per round from `mrCreatedAt`, restamped on each entry into CI_POLLING, kept by a repeat, else `requestOpenedAt`.

#### Scenario: A wait on the reviewers outlives the window
- **WHEN** ABC-42 is `REVIEWED`, `waitingMaxIntervalMinutes` is 240 and 36h have passed since its round began
- **THEN** it is still watched and read every 150 minutes, owned by CI with no attention asked, until approved, merged or closed

#### Scenario: Polling stopped
- **WHEN** a round not waiting on reviewers outlives `autoReview.windowHours`
- **THEN** the card says `polling stopped` and asks for you, and one banner says so, none again after a restart
