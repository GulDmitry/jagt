## MODIFIED Requirements

### Requirement: The human keeps what mine and humanPaths name
`roles.master.mine` SHALL name the steps (`task/master/MasterRight`) and verbs the human keeps, any `TaskAction` or `GlobalCommand` id.
`humanPaths` keeps a task's ship and deploy where it changes a path they name (`HumanPaths`). The Master SHALL press every other verb through
`press_task` and `run_command` as a human's press, held to a Master case. A command's `whyNotTheMaster` bars it, whatever `mine` says.

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

#### Scenario: The answer is a move the Master holds
- **WHEN** the answer to a question is `ship` or `deploy` and `mine` keeps neither it nor its step
- **THEN** the Master presses that move as a human's press, through `FlowRules` and the unattended-pace count, and relays no answer text
- **AND** a refused press, as one past the pace ceiling, is logged as the Master's refused press, relays nothing and leaves the question to you

#### Scenario: The answer is a move the Master does not hold
- **WHEN** the answer names `ship` or `deploy` and `mine` keeps it, or names any other move
- **THEN** the answer is relayed as text to the session

#### Scenario: The tree changed after the Master read it
- **WHEN** the tree or the drafts differ from what the Master read for its answer
- **THEN** nothing is pressed and the answer is relayed
- **AND** the next hand-back is read by the Master anew

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
