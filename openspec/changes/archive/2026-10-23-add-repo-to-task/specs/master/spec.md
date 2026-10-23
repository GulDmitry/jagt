## MODIFIED Requirements

### Requirement: In act the Master moves for you
With `master.mode: act` the Master SHALL ship a `ready` round, approve a plan that holds as `approve-plan` does, press
`add-repo` for a repository a session asked for (`MasterRepoJob`), and answer a question. A permission, and a plan
asked for in `plan` mode, stay yours. It SHALL deploy a `REVIEWED` or `APPROVED` task once (`MasterDeployJob`). `master.mine` SHALL keep named steps (`task/MasterRight`) the human's,
`[plan]` while unset; a project's `humanPaths` a task's ship and deploy where it changes a path they name
(`HumanPaths`). Closing SHALL stay the human's: the Master's `done` is refused.

#### Scenario: Green with every thread closed
- **WHEN** a task reaches `REVIEWED` or `APPROVED`
- **THEN** the Master deploys it, never reverts; `mine: [deploy, revert]` leaves the press yours

#### Scenario: A ready round holds nothing to ship
- **WHEN** it has nothing to ship
- **THEN** it presses nothing; the task waits for you

#### Scenario: A ship or a reply kept for you
- **WHEN** a round is `ready` and `mine: [ship]`, or the Master ships and `mine: [reply]`
- **THEN** it ships nothing, or the session leaves `review_replies.md` for you

#### Scenario: A path kept for you
- **WHEN** `humanPaths: ["**/auth/**"]` names a changed path, or the paths are unread
- **THEN** it presses neither; the `master` window names the path

#### Scenario: A session asks for a repository
- **WHEN** a session names a configured project it lacks and the Master holds `repo`
- **THEN** it presses `add-repo` once per request, answers in no prose and pings nobody

#### Scenario: A repository kept for you
- **WHEN** `mine: [repo]`
- **THEN** it presses and answers nothing; you are pinged once and `add-repo` is yours

#### Scenario: A session stops to ask
- **WHEN** a session asks a question
- **THEN** the Master answers in its window, the task resumes; `mine: [answer]` keeps it yours

#### Scenario: A plan in act
- **WHEN** a plan holds and `mine` is unset, or `mine: [plan]`, or the task was opened in `plan` mode
- **THEN** it approves nothing; the plan waits for your `approve-plan`

#### Scenario: A question with a plan kept for you
- **WHEN** a session asks at `PLAN_PENDING` and the plan is yours to approve
- **THEN** the Master never answers; the plan and its question wait for you

#### Scenario: An answer while jagt's instruction waits
- **WHEN** it answers at `SHIPPING` or `DEPLOY_CONFLICT`
- **THEN** the answer goes under the ship or hand-off in `task_context.md`, which still stands

#### Scenario: A session asks for a permission
- **WHEN** it reports `outcome=permission`, stopped on a call its CLI denied
- **THEN** the Master never answers: no model grants a right, so the question is yours

#### Scenario: Questions repeat over an unchanged tree
- **WHEN** they repeat
- **THEN** after three, a fresh session and an answer that changes the worktrees; the ninth is a runaway, yours

#### Scenario: It is unsure what the ticket asks
- **WHEN** the ticket could mean "switch" or "add beside"
- **THEN** it decides as you would, sent back as a finding

### Requirement: A task's work stays in the task
Work a task needs SHALL be done in it, on its own branch, and the Master SHALL open no task: a `do` line in its answer
opens nothing, the session told only you open a task. A repository the task lacks is asked for with `needsProject`
and attached by `add-repo`, never opened as a task.

#### Scenario: The Master's answer holds a `do` line
- **WHEN** in `act` it answers a session with `do proj drop the old keys from main`
- **THEN** no task opens; the session does that work on its own branch
