## MODIFIED Requirements

### Requirement: In act the Master moves for you
With `roles.master.mode: act` the Master SHALL ship a `ready` round, approve a plan that holds as `approve-plan` does, and answer a question. It
SHALL press `add-repo` for a repository a session asked for (`MasterRepoJob`) and deploy a `REVIEWED` or `APPROVED` task once (`MasterDeployJob`).
It SHALL decide a stop for a suspected loop ([task-stops](../task-stops/spec.md)); `mine: [lift]` keeps it yours. Unset, the mode is `act`.

#### Scenario: What it presses in act
- **WHEN** a task reaches `REVIEWED` or `APPROVED`
- **THEN** the Master deploys it, never reverts, unless `mine: [deploy]`, as unset (`mine: [plan, deploy, revert]`); `mine: []` hands it the press
- **WHEN** the press is refused, as a deploy of ABC-42 with no commit beyond dev
- **THEN** one WARN names the task, the verb and the cause, no ERROR; the other tasks of the tick are still served
- **WHEN** a hand-back has nothing new to ship: branch pushed unchanged, nothing uncommitted, no replies drafted
- **THEN** it presses nothing, and the task waits for you; an unpushed commit or uncommitted work makes it due
- **WHEN** replies are drafted
- **THEN** it ships or holds as P1 to P5 and B1 of [review-actors](../review-actors/spec.md) say, never by a rule of its own
