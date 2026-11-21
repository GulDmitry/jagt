## ADDED Requirements

### Requirement: The Master acts unless the human chose otherwise
An unset `roles.master.mode` SHALL be `act` where the agent CLI lets jagt read what the human typed, else `off`. An unset `roles.master.mine` SHALL be
`[plan, deploy, revert]`: the plan approval and the deploy stay the human's, every other step the Master's. `judge` and `off` stay one written word away.

#### Scenario: A fresh install
- **WHEN** `jagt.yml` writes no `roles.master.mode` and no `mine`
- **THEN** the Master acts and keeps `plan`, `deploy` and `revert` for you
- **WHEN** `mine: []` is written
- **THEN** the Master holds every step
- **WHEN** three tasks in a row it moved end badly
- **THEN** it judges until you press `act stopped`

## MODIFIED Requirements

### Requirement: The Master is a headless run per role, not a window
One headless run per brief role SHALL read each round in the worktrees and present `agent.sandbox.allowWrite` dirs, loading no worktree setting.
jagt SHALL store each answer with the hand-back it asked about (`MasterReview`), `master-review.md` only its rendering;
a round SHALL cost one heavy read per role it applies to, charged as `master`, later roles reading the cached round.

#### Scenario: What a role may call
- **WHEN** a role reads
- **THEN** read-only git, `-C` naming a worktree, plain words, option by option, `--no-ext-diff --no-textconv`
- **AND** only the MCP tools `allowed-tools` names; it writes nothing, its session included
- **AND** jagt refuses any other call, your allow rules included (`ReadGate`)

#### Scenario: A round read before you look
- **WHEN** `roles.master.mode` is `judge`
- **THEN** each round is read into the worktree's `master-review.md`; it presses nothing
- **AND** a verdict answers only the hand-back it was asked about, however late it is written; a file jagt did not write answers none
- **WHEN** it is under review
- **THEN** the chip says `master review` and nothing asks you; the session was told to end its turn

#### Scenario: Which roles read, and on what model
- **WHEN** a role's row names a `model`
- **THEN** it runs on it; blank keeps `roles.master.model`
- **WHEN** the diff touches none of a role's `applies` globs, or only paths its `!` globs name
- **THEN** a `# skipped` line replaces its read; unread paths, or no role left, read every role
- **WHEN** a session hands back a round
- **THEN** its `<self_review>` reads it first, then the Master by the same roles unless its brief names others

### Requirement: In act the Master moves for you
With `roles.master.mode: act` the Master SHALL ship a `ready` round, approve a plan that holds as `approve-plan` does, and answer a question. It
SHALL press `add-repo` for a repository a session asked for (`MasterRepoJob`) and deploy a `REVIEWED` or `APPROVED` task once (`MasterDeployJob`).
It SHALL decide a stop for a suspected loop ([task-stops](../task-stops/spec.md)); `mine: [lift]` keeps it yours.

#### Scenario: What it presses in act
- **WHEN** a task reaches `REVIEWED` or `APPROVED`
- **THEN** the Master deploys it, never reverts, unless `mine` names `deploy`, as it does unset; `mine: []` hands it the press
- **WHEN** the press is refused, as a deploy of ABC-42 with no commit beyond dev
- **THEN** one WARN names the task, the verb and the cause, no ERROR; the other tasks of the tick are still served
- **WHEN** a hand-back has nothing new to ship: branch pushed unchanged, nothing uncommitted, no replies drafted
- **THEN** it presses nothing, and the task waits for you; an unpushed commit, uncommitted work or replies drafted since the last ship make it due

### Requirement: The human keeps what mine and humanPaths name
`roles.master.mine` SHALL name the steps (`task/master/MasterRight`) and verbs the human keeps, any `TaskAction` or `GlobalCommand` id.
`humanPaths` keeps a
task's ship and deploy where it changes a path they name (`HumanPaths`). The Master SHALL press every other verb through `press_task` and
`run_command` as a human's press, held to a Master case. A command declares `whyNotTheMaster` where the Master never runs it, whatever `mine` says.

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
- **WHEN** a plan holds and `mine` is unset, or names `plan`, or the task was opened in `plan` mode
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
