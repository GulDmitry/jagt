## MODIFIED Requirements

### Requirement: The polling window is per round
The window SHALL run per round from `mrCreatedAt`, restamped on each entry into CI_POLLING, kept by a repeat, else `requestOpenedAt`.

#### Scenario: Polling stopped
- **WHEN** a round not waiting on reviewers outlives `autoReview.windowHours`
- **THEN** the card says `polling stopped` and asks for you, and one banner says so, none again after a restart
