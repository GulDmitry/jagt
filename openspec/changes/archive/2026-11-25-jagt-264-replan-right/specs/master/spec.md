## MODIFIED Requirements

### Requirement: In act the Master moves for you
With `roles.master.mode: act` the Master SHALL ship a `ready` round, approve a plan that holds as `approve-plan` does, and answer a question. It
SHALL press `add-repo` for a repository a session asked for (`MasterRepoJob`) and deploy a `REVIEWED` or `APPROVED` task once (`MasterDeployJob`).
It SHALL decide a stop for a suspected loop ([task-stops](../task-stops/spec.md)); `mine: [lift]` keeps it yours. Unset, the mode is `act`.

#### Scenario: What it presses in act
- **WHEN** a task reaches `REVIEWED` or `APPROVED`
- **THEN** the Master deploys it, never reverts, unless `mine: [deploy]`, as unset (`mine: [plan, replan, deploy, revert]`); `mine: []` hands it the press
- **WHEN** the press is refused, as a deploy of ABC-42 with no commit beyond dev
- **THEN** one WARN names the task, the verb and the cause, no ERROR; the other tasks of the tick are still served
- **WHEN** a hand-back has nothing new to ship: branch pushed unchanged, nothing uncommitted, no replies drafted
- **THEN** it presses nothing, and the task waits for you; an unpushed commit or uncommitted work makes it due
- **WHEN** replies are drafted
- **THEN** it ships or holds as P1 to P5 and B1 of [review-actors](../review-actors/spec.md) say, never by a rule of its own

### Requirement: The human keeps what mine and humanPaths name
`roles.master.mine` SHALL name the steps (`task/master/MasterRight`) and verbs the human keeps, any `TaskAction` or `GlobalCommand` id.
`humanPaths` keeps a task's ship and deploy where it changes a path they name (`HumanPaths`). The Master SHALL press every other verb through `press_task` and
`run_command` as a human's press, held to a Master case. A command declares `whyNotTheMaster` where the Master never runs it, whatever `mine` says.

#### Scenario: A ship, a reply or a path kept for you
- **WHEN** a round is `ready` and `mine: [ship]`, or the Master ships and `mine: [reply]`
- **THEN** it ships nothing, or the session leaves `review_replies.md` for you
- **WHEN** `humanPaths: ["**/auth/**"]` names a changed path, or the paths are unread
- **THEN** it presses neither; the Master's log names the path

#### Scenario: A verb pressed or kept
- **WHEN** the Master presses `trust`, `lift`, `escalate`, `pause`, `done` or `remove-repo` and a Master case allows it at the status
- **THEN** it runs as a human's press, stamped the Master's; where `mine` names the verb it is refused with `ACTION_NOT_AVAILABLE`

#### Scenario: A command is closed to the Master
- **WHEN** the Master runs `do`, `resume`, `act` or `jobs`
- **THEN** the first three are refused with their own reason, `jobs` runs as the Master's

#### Scenario: A session asks for a repository, or it is kept for you
- **WHEN** a session names a configured project it lacks and the Master holds `repo`
- **THEN** it presses `add-repo` once per request, answers in no prose, pings nobody; with `mine: [repo]` it presses nothing, you are pinged once

#### Scenario: A session stops to ask
- **WHEN** a session asks a question
- **THEN** the Master answers in its window, the task resumes; `mine: [answer]` keeps it yours; the same words asked again are a new question
- **AND** a ticket the question names is read from the tracker and answered by its status; unreadable, the question is yours
- **AND** asked which of the ticket's own routes to take, it picks the one needing no right the session lacks, and says why

#### Scenario: A plan in act
- **WHEN** `mine` is unset, the task opened in `plan` mode, or `mine` holds the plan's right (`plan` a task's first, `replan` each later)
- **THEN** it approves nothing; the plan waits for your `approve-plan`
- **WHEN** a later plan holds and `mine: [plan]`, or a first plan and `mine: [replan]`
- **THEN** the Master approves it and no press is asked of you

#### Scenario: A plan handed back with a question
- **WHEN** a session hands back `PLAN_PENDING` with a question, whatever `mine` holds
- **THEN** the Master reviews the plan; the question is answered by that review, not by an answer without the plan
- **AND** a `ready` verdict sends its findings first, then approves only where it may

#### Scenario: An answer while jagt's instruction waits
- **WHEN** it answers at `SHIPPING` or `DEPLOY_CONFLICT`
- **THEN** the answer goes under the ship or hand-off in `task_context.md`, which still stands

#### Scenario: A session asks for a permission
- **WHEN** it reports `outcome=permission`, stopped on a call its CLI denied
- **THEN** the Master never answers: no model grants a right, so the question is yours
- **AND** when they repeat, after three an answer that changes the approach, in the same session; the ninth is a runaway, yours

#### Scenario: It is unsure what the ticket asks
- **WHEN** the ticket could mean "switch" or "add beside"
- **THEN** it decides as you would, sent back as a finding
