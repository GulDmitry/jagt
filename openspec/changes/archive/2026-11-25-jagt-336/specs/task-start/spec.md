## MODIFIED Requirements

### Requirement: A task may plan before it has a repository
The board form's `plan first` with no project ticked SHALL start a task with no project, never routed (`ProjectRouting` is not asked).
A typed `do <ticket>` without a project and tracker intake SHALL still go through the router.
Its planning session SHALL run in `scratch/<slug>`, a sibling of `state.json` under jagt's work dir: no git
(`GIT_CEILING_DIRECTORIES` set for that session, `scratch/` gitignored), no push, no ship; the plan gate holds there as anywhere.
#### Scenario: Planning with no project
- **WHEN** you press Start with `plan first` ticked, no project ticked, and notes
- **THEN** the task is `PLAN_PENDING`-bound, its session runs in `scratch/<slug>` and no repository is cut
- **WHEN** a task holds only its scratch folder
- **THEN** it holds no worktree, branch or request, a push, `ship` and `deploy` are unavailable and `scratch/` is untracked
- **AND** its repositories begin with `approve-plan`
#### Scenario: A start the server refuses
- **WHEN** a start names no project and is not `plan first`
- **THEN** it is refused and nothing is created
#### Scenario: Routing stays for typed and tracker starts
- **WHEN** you type `do ABC-42` with no project, or intake opens ABC-42
- **THEN** the project is chosen by routing as before
#### Scenario: Done
- **WHEN** you `done` a task that never left its scratch folder
- **THEN** `done` deletes the folder

### Requirement: An approved plan places the task
`approve-plan` on a task with no repository SHALL cut a worktree per `[changed]` key of the plan's `## Repositories`, the first primary.
The scratch folder SHALL be deleted after the state write; the session starts fresh in the primary and the task is `IN_PROGRESS`.
#### Scenario: A plan-first session with no project
- **WHEN** such a task's session is briefed
- **THEN** it is told it plans in a scratch folder
- **AND** it reads that `plan.md` names every project the task changes, configured or not, as a `- [changed] <key>` under `## Repositories`
#### Scenario: Placed
- **WHEN** you approve a plan listing `- [changed] api` and `- [changed] web`, both configured
- **THEN** worktrees exist for both, with `plan.md` and the task's other artifacts copied into them
- **AND** `api` runs the session fresh, the scratch folder is gone, the task is `IN_PROGRESS`
#### Scenario: An existing ticket branch
- **WHEN** the plan's repository already holds ABC-42's branch
- **THEN** its own commits are resumed, else the branch is recreated, as the intake rule says
#### Scenario: A key jagt.yml lacks
- **WHEN** the plan lists `- [changed] billing`, not configured, or a typed base branch a named project lacks
- **THEN** nothing is cut, the task stays `PLAN_PENDING` and the question names `billing` and the add-project form
#### Scenario: The Master approves
- **WHEN** the Master in `act` approves that plan
- **THEN** the Master never adds a project: nothing is cut, and the session is told to revise the plan: `billing` and the registered projects
#### Scenario: A cut fails
- **WHEN** the second worktree's cut fails
- **THEN** every cut is unwound, the first is removed, and the task keeps its scratch folder

### Requirement: An existing branch is never lost
Nothing SHALL be freed or moved until `recreate` or `resume` is picked for a branch that exists.
A branch checked out in the base repository SHALL be freed and detached in place, a warning naming it.
Uncommitted tracked changes there, or another worktree holding it, MUST refuse naming the directory; untracked files block nothing.
#### Scenario: Intake meets a leftover branch
- **WHEN** intake finds the branch
- **THEN** it resumes if the branch or its origin copy holds commits the base lacks, else recreates; neither loses work
#### Scenario: Tracked changes in the way
- **WHEN** uncommitted tracked changes or another worktree block the start
- **THEN** commit, stash, or free it

## ADDED Requirements

### Requirement: A start the server never finished is kept
A start SHALL be recorded, with its request, in `pending-starts.json` beside `state.json` before its first read or cut.
The record SHALL end once the task's state is written, and on every refusal.
A restart SHALL log `task start interrupted` with `key` and `cause` per leftover and keep it.
It SHALL log it once, on the first read of the record.
It ends on `resume <key>`, which runs the saved request again, on `resume <key> discard`, or once a task has that key.
No task status exists for it.
#### Scenario: The server stops while the ticket is read
- **WHEN** jagt restarts after `do ABC-42 tighten the parser` was cut short at the ticket read
- **THEN** no task exists, the record holds `ABC-42` and the words, and the head shows one mark with them in its hover
#### Scenario: Written words are cut short
- **WHEN** a start of your own words is cut short before its branch is named
- **THEN** its record holds the words under a generated key
#### Scenario: Resume or discard
- **WHEN** you run `resume ABC-42`, or `resume ABC-42 discard`
- **THEN** the start runs again from the saved words, or the record is deleted; either way the mark ends

## REMOVED Requirements

### Requirement: A scratch folder is no repository
**Reason**: Folded into "A task may plan before it has a repository".
**Migration**: Read that requirement.

### Requirement: A task with no repository plans its repositories
**Reason**: Folded into "An approved plan places the task".
**Migration**: Read that requirement.

### Requirement: The base repository gives up the branch safely
**Reason**: Folded into "An existing branch is never lost".
**Migration**: Read that requirement.
