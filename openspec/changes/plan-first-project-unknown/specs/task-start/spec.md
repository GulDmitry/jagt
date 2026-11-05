## ADDED Requirements

### Requirement: A task may plan before it has a repository
The board form's `plan first` with no project ticked SHALL start a task with no project, never routed (`ProjectRouting` is not asked).
A typed `do <ticket>` without a project and tracker intake SHALL still go through the router.
A start with no project and no `plan first` SHALL be refused by the server, and nothing is created.
Its planning session SHALL run in `scratch/<slug>`, a sibling of `state.json` under jagt's work dir: no git
(`GIT_CEILING_DIRECTORIES` set for that session, `scratch/` gitignored), no push, no ship; the plan gate holds there as anywhere.
`done` on such a task SHALL delete its scratch folder.

#### Scenario: Planning with no project
- **WHEN** you press Start with `plan first` ticked, no project ticked, and notes
- **THEN** the task is `PLAN_PENDING`-bound, its session runs in `scratch/<slug>` and no repository is cut

#### Scenario: A start the server refuses
- **WHEN** a start names no project and is not `plan first`
- **THEN** it is refused and nothing is created

#### Scenario: Routing stays for typed and tracker starts
- **WHEN** you type `do ABC-42` with no project, or intake opens ABC-42
- **THEN** the project is chosen by routing as before

#### Scenario: Done
- **WHEN** you `done` a task that never left its scratch folder
- **THEN** the folder is deleted

### Requirement: An approved plan places the task
`approve-plan` on a task with no repository SHALL cut a worktree per `[changed]` key of the plan's `## Repositories`,
the first `[changed]` key primary, and copy `plan.md` and the task's other artifacts into it.
An existing ticket branch follows the intake rule: its own commits are resumed, else it is recreated.
The scratch folder SHALL be deleted after the state write; the session starts fresh in the primary and the task is `IN_PROGRESS`.
A cut that fails SHALL unwind every cut and the task keeps its scratch folder.
A `[changed]` key `jagt.yml` lacks, or a typed base branch a named project lacks, SHALL cut nothing:
the task stays `PLAN_PENDING` and the human's question names the keys and points at the add-project form.
The Master in `act` never adds a project; its approval yields the same question.

#### Scenario: Placed
- **WHEN** you approve a plan listing `- [changed] api` and `- [changed] web`, both configured
- **THEN** worktrees exist for both, `api` runs the session fresh with the plan copied in, the scratch folder is gone, the task is `IN_PROGRESS`

#### Scenario: A key jagt.yml lacks
- **WHEN** the plan lists `- [changed] billing`, not configured
- **THEN** nothing is cut, the task stays `PLAN_PENDING` and the question names `billing` and the add-project form

#### Scenario: The Master approves
- **WHEN** the Master in `act` approves that plan
- **THEN** no project is added and the same question stands

#### Scenario: A cut fails
- **WHEN** the second worktree's cut fails
- **THEN** the first is removed and the task keeps its scratch folder
