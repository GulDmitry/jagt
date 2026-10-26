## MODIFIED Requirements

### Requirement: In act the Master moves for you
With `master.mode: act` the Master SHALL ship a `ready` round, approve a plan that holds as `approve-plan` does, and
answer a question. It SHALL press `add-repo` for a repository a session asked for (`MasterRepoJob`) and deploy a
`REVIEWED` or `APPROVED` task once (`MasterDeployJob`). A permission, and a plan asked for in `plan` mode, stay yours.

#### Scenario: What it presses in act
- **WHEN** a task reaches `REVIEWED` or `APPROVED`
- **THEN** the Master deploys it, never reverts; `mine: [deploy, revert]` leaves the press yours
- **WHEN** the press is refused, as a deploy of ABC-42 with no commit beyond dev
- **THEN** one WARN names the task, the verb and the cause, no ERROR; the other tasks of the tick are still served
- **WHEN** a round has nothing to ship
- **THEN** it presses nothing; the task waits for you

#### Scenario: A hand-back with nothing new is not shipped
- **WHEN** a round is handed back, its branch is pushed unchanged, nothing is uncommitted and no replies are drafted
- **THEN** the Master presses nothing; new uncommitted work, an unpushed commit or drafted replies make it due

