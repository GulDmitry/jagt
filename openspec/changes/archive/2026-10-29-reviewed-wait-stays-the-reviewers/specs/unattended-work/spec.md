## MODIFIED Requirements

### Requirement: An open request is what the poller watches
At `REVIEWED`, `APPROVED` or `DEPLOYED` only a reviewer moves it on, so it SHALL be read every `maxIntervalMinutes`, past its window lengthening to `waitingMaxIntervalMinutes` (240), never stopping.

### Requirement: The polling window is per round

#### Scenario: A wait on the reviewers outlives the window
- **WHEN** ABC-42 is `REVIEWED` and 36h have passed since its round began
- **THEN** it is still watched and read every 150 minutes, owned by CI with no attention asked, until approved, merged or closed

#### Scenario: Polling stopped
- **WHEN** a round not waiting on reviewers outlives `autoReview.windowHours`
- **THEN** the card says `polling stopped` and asks for you
