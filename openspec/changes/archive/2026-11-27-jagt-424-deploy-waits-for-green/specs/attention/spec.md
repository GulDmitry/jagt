## MODIFIED Requirements

### Requirement: An approved or deployed task's owner
`REVIEWED`'s owner SHALL be the review request: nothing highlighted, no ping, `deploy` listed where no approval is needed. Only `APPROVED` taps the
human, and neither is theirs where the Master deploys it in `act`; `YourMoveJob` MUST stay silent unless `Move` says YOU. An `APPROVED` request
whose checks are not green on its head is the host's until they read green. `DEPLOYED`'s owner is nobody, as `DONE`'s; `done` stays highlighted.
#### Scenario: An approved task
- **WHEN** `APPROVED`
- **THEN** you are pinged; once !571, its only request, is merged, the card reads `merged` and the badge `you can close the task`
- **WHEN** ABC-42 is `APPROVED` while its checks run
- **THEN** no badge and no `deploy` until they read green
