# task-flow Specification

## Purpose

A task's life: which status allows which action, where each outcome leads, and how work starts.

## Requirements

### Requirement: One table owns the life of a task
`flow/FlowRules` SHALL hold, in one Java file the compiler checks, which statuses allow which action and what each
outcome leads to. Its guard MUST read `flow/Facts`: an open request, and a liveness probe the projection passes as
"no", since it costs a process spawn per row.

#### Scenario: An action is asked
- **WHEN** an action is asked
- **THEN** `FlowRules` allows or refuses it

### Requirement: Fourteen statuses
A task SHALL be: `NEW` nothing reported; `PLAN_PENDING` a plan waits for you; `IN_PROGRESS` the agent works;
`VERIFYING` jagt runs the project's command; `REVIEW_PENDING` back with the human; `SHIPPING` a push in flight;
`CI_POLLING` a round open; `CI_FAILED` checks red; `REVIEWED` nothing unresolved, CI green, unapproved;
`APPROVED` by a human; `DEPLOY_CONFLICT` resolved in the deploy worktree; `DEPLOYED` live on the deploy branch;
`REVERTED` deploy out, branch and commits kept; `DONE` closed.

#### Scenario: Nothing reported
- **WHEN** the agent has reported nothing
- **THEN** the task is `NEW`

### Requirement: A verb a human keeps is named in the table
`TaskAction.humanOnly` SHALL mark it, never prose; `done` alone is. A person opens a task and closes it; the work
between is what the Master stands in for where `master.mode` is `act` ([master](../master/spec.md)).

#### Scenario: Closing a task
- **WHEN** `done` runs
- **THEN** a human pressed it

### Requirement: Door one runs an action
`flow/FlowEngine.run` SHALL check the rules, run the action's `port/TaskCapability`, and write the status the table
gives for its `flow/Outcome`.

#### Scenario: A verb runs
- **WHEN** an allowed action finishes
- **THEN** its outcome sets the status

### Requirement: Door two takes a reported status
`flow/FlowReports` SHALL take a status the task reports, by its agent over MCP or a round jagt read for it, refused
unless `FlowRules.refusedReport` allows it. The refusal MUST own the reason the agent acts on, so no task talks
itself onto a shared branch, out of one, or closed.

#### Scenario: A forbidden report
- **WHEN** `FlowRules.refusedReport` refuses an agent's report
- **THEN** the agent gets the reason

### Requirement: A hand-back waits at VERIFYING
`FlowRules.reported` SHALL redirect a hand-back owing a verification run to `VERIFYING`; no action leads there and
no agent may report it. `VerifyJob` runs the command: green reaches the human, red MUST go back to the session with
the output. `TaskStatus.heldByJagt` keeps the watchdog off that silence.

#### Scenario: A red verification run
- **WHEN** `verifyCommand` goes red
- **THEN** the session gets it, not you

### Requirement: Every deploy is checked where it landed
`DeployCheckJob` SHALL ask the session once per deploy commit, keyed off what it asked, not the context file other
relays overwrite; how anything reaches it is the session's. Green MUST report nothing, the task staying
`DEPLOYED`; red is the session's `IN_PROGRESS`, the round running again: reviewed, deployed, checked.

#### Scenario: A deployed change fails its check
- **WHEN** the check fails
- **THEN** the task is `IN_PROGRESS`

### Requirement: In act a deploy conflict is the session's
`DeployConflictJob` SHALL hand the session the deploy worktree once per conflict; a resolution staged in full MUST
finish the press the human made ([0013](../../../docs/decisions/0013-a-deploy-conflict-is-the-sessions-in-act.md)).

#### Scenario: A conflict where the Master acts
- **WHEN** the session stages its resolution in full
- **THEN** jagt finishes the deploy

### Requirement: Statuses a report cannot move
A status a human owns SHALL be held, not refused: `FlowRules.reported` keeps a `REVERTED` task and records the
line. Refusing errors every call of that session; passing the next status erases the revert and launders the
`CI_POLLING` guard through `IN_PROGRESS`. `REVIEWED` or `APPROVED` read off the request MUST leave a `DEPLOYED`,
`DEPLOY_CONFLICT` or `DONE` task in place, or a poll drags shipped work back to asking for approval.

#### Scenario: A report at REVERTED
- **WHEN** a `REVERTED` task's agent reports
- **THEN** the task stays, the line recorded

#### Scenario: An approval after deploy
- **WHEN** a poll reads `APPROVED` past `DEPLOYED`
- **THEN** the task stays

### Requirement: Nothing below flow decides a status
A capability SHALL report `OK` / `RELAYED` / `CONFLICT` / `PARTIAL` / `GONE` plus the sentence and the stamp, so
work serves several statuses. Its sentence MAY name the status (`DeployService` says `; DEPLOYED` /
`; REVERTED`); nothing parses it. `withStatus` MUST appear only in `flow/` and its implementing record.

#### Scenario: Grepping for withStatus
- **WHEN** the sources are grepped
- **THEN** only `flow/` and its record match

### Requirement: PARTIAL refuses
`PARTIAL` SHALL be the one refusing outcome, stamped on the task first and thrown second: a shared branch holding
half a change must be recorded, not merely complained about.

#### Scenario: Half a change lands
- **WHEN** a capability returns `PARTIAL`
- **THEN** the task is stamped, then it throws

### Requirement: Starting a task
`do` SHALL cut a branch from the project's base into its own worktree, an agent in it. The launch row
MUST offer ticket, project, base branch, `plan first`, notes, branch strategy and Start.

#### Scenario: New ticket
- **WHEN** you run `do ABC-42`, or `do ABC-42 <project>` to skip guessing the project
- **THEN** branch `ABC-42` is cut from the project's base, in its own worktree, an agent in it

#### Scenario: Work with no ticket
- **WHEN** you run `do <project> <what to do…>`
- **THEN** your words are the instructions and name the branch

#### Scenario: Plan before code
- **WHEN** you pick `plan first`
- **THEN** the card reads `plan waiting`; your next instruction to the session approves it

#### Scenario: Starting from another branch
- **WHEN** you run `do ABC-42 from <branch>`
- **THEN** it is cut from that branch and the request targets it; deploy still goes to `deployBranch`

### Requirement: An existing branch is never lost
Nothing SHALL be freed or moved until `recreate` or `resume` is picked for a branch that exists.

#### Scenario: Intake meets a leftover branch
- **WHEN** intake finds the branch
- **THEN** it resumes if the branch or its origin copy holds commits the base lacks, else recreates; neither loses work

### Requirement: The base repository gives up the branch safely
A branch checked out in the base repository SHALL be freed and detached in place, a warning naming it. Uncommitted
tracked changes there, or another worktree holding it, MUST refuse naming the directory; untracked files block nothing.

#### Scenario: Tracked changes in the way
- **WHEN** uncommitted tracked changes or another worktree block the start
- **THEN** commit, stash, or free it

### Requirement: A worktree carries what the project needs
`worktree.copyGlobs` SHALL copy gitignored files (`.env`, key, cert) into every new worktree at the same path. A
repository's own `CLAUDE.md`, `AGENTS.md`, Codex config and git hooks MUST stay untouched and in force, jagt's
briefing and guard beside them. A push of any branch but the task's MUST be refused before anything leaves the machine.

#### Scenario: The agent pushes a foreign branch
- **WHEN** it pushes another branch
- **THEN** nothing leaves the machine
