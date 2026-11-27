## ADDED Requirements

### Requirement: The brief carries the ticket text jagt read
An acceptance run SHALL be given the ticket text jagt kept for the task, never one the run must fetch itself.
#### Scenario: The ticket was read at intake
- **WHEN** jagt kept ABC-42's ticket text and acceptance runs on its deploy
- **THEN** the run's brief carries that text

### Requirement: A check without its inputs stops on a read
A check SHALL open only with the deploy's worktree and, where the task has a ticket, its text; a text not kept is read once more first.
A check missing one SHALL stop the task on `ACCEPTANCE_UNREAD`, naming `ticket` or `deploy`, and give no verdict.
`ACCEPTANCE_UNREAD` SHALL be a `BROKEN` stop ending on `INPUT_READ`, its advice never to lift.
Each tick of the check SHALL read the inputs again; once found, the stop ends and the check runs.
#### Scenario: The ticket cannot be read
- **WHEN** ABC-42 is DEPLOYED, has a ticket, and jagt holds no text of it after reading again
- **THEN** no check runs and ABC-42 stops on `ACCEPTANCE_UNREAD` naming `ticket`, not on `ACCEPTANCE_FAILED`
#### Scenario: The deploy cannot be read
- **WHEN** ABC-42 is DEPLOYED with no worktree to read its deploy from
- **THEN** no check runs and ABC-42 stops on `ACCEPTANCE_UNREAD` naming `deploy`
#### Scenario: The input comes back
- **WHEN** ABC-42 stands on `ACCEPTANCE_UNREAD` and its ticket text is now kept
- **THEN** the stop ends without a lift and the check runs
