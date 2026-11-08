## MODIFIED Requirements

### Requirement: The human keeps what mine and humanPaths name
`roles.master.mine` SHALL keep named steps (`task/MasterRight`) and verbs (any `TaskAction` or `GlobalCommand` id) the human's, `[plan]`
while unset; a project's `humanPaths` keeps a task's ship and deploy where it changes a path they name (`HumanPaths`). Every other verb the
Master SHALL press through `press_task` and `run_command`, as a human's press, held to a Master case; `plan` keeps `approve-plan`, `repo` `add-repo`.
A command SHALL declare `whyNotTheMaster` where the Master never runs it, whatever `mine` says.

#### Scenario: A ship, a reply or a path kept for you
- **WHEN** a round is `ready` and `mine: [ship]`, or the Master ships and `mine: [reply]`
- **THEN** it ships nothing, or the session leaves `review_replies.md` for you
- **WHEN** `humanPaths: ["**/auth/**"]` names a changed path, or the paths are unread
- **THEN** it presses neither; the `master` window names the path

#### Scenario: A verb pressed or kept
- **WHEN** the Master presses `trust`, `lift`, `escalate`, `pause`, `done` or `remove-repo` and a Master case allows it at the status
- **THEN** it runs as a human's press, stamped the Master's; where `mine` names the verb it is refused with `ACTION_NOT_AVAILABLE`

#### Scenario: A command is closed to the Master
- **WHEN** the Master runs `do`, `resume`, `act` or `jobs`
- **THEN** the first three are refused with their own reason, `jobs` runs as the Master's

#### Scenario: A session asks for a repository, or it is kept for you
- **WHEN** a session names a configured project it lacks and the Master holds `repo`
- **THEN** it presses `add-repo` once per request, answers in no prose and pings nobody
- **AND** with `mine: [repo]` it presses and answers nothing; you are pinged once and `add-repo` is yours

#### Scenario: A session stops to ask
- **WHEN** a session asks a question
- **THEN** the Master answers in its window, the task resumes; `mine: [answer]` keeps it yours; the same words asked again are a new question

#### Scenario: A plan in act
- **WHEN** a plan holds and `mine` is unset, or `mine: [plan]`, or the task was opened in `plan` mode
- **THEN** it approves nothing; the plan waits for your `approve-plan`

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
