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
- **THEN** it presses nothing, and the task waits for you; an unpushed commit or uncommitted work makes it due, unless drafted replies wait for you


## ADDED Requirements

### Requirement: The Master ships over no draft the human must read
The Master in act SHALL NOT press ship while drafted replies wait that a ship will not post, nor, unless `codeReview.repliesToPeopleUnasked`
is `true` (default `false`), while any wait (`ReviewDrafts.heldForHuman`). Where it is `true`, every reply a ship posts SHALL open with
the AI line `NotePoster` puts on the code reviewer's notes ([code-review](../code-review/spec.md)).
#### Scenario: Replies to people wait
- **WHEN** drafted replies wait and `repliesToPeopleUnasked` is unset
- **THEN** it presses nothing and the move stays yours, whatever else is new to ship
#### Scenario: Posting to people allowed
- **WHEN** `repliesToPeopleUnasked: true` and a ship posts every draft
- **THEN** the Master may ship, and each posted reply opens with the AI line
