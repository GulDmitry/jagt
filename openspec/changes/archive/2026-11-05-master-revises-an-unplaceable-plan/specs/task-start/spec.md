## MODIFIED Requirements

### Requirement: An approved plan places the task
`approve-plan` on a task with no repository SHALL cut a worktree per `[changed]` key of the plan's `## Repositories`, the first primary.
It SHALL copy `plan.md` and the task's other artifacts into it.
An existing ticket branch follows the intake rule: its own commits are resumed, else it is recreated.
The scratch folder SHALL be deleted after the state write; the session starts fresh in the primary and the task is `IN_PROGRESS`.
A cut that fails SHALL unwind every cut and the task keeps its scratch folder.
A `[changed]` key `jagt.yml` lacks, or a typed base branch a named project lacks, SHALL cut nothing:
the task stays `PLAN_PENDING` and the human's question names the keys and points at the add-project form.
The Master in `act` never adds a project; its approval SHALL return the plan to the session, naming those keys and the registered projects.

#### Scenario: Placed
- **WHEN** you approve a plan listing `- [changed] api` and `- [changed] web`, both configured
- **THEN** worktrees exist for both, `api` runs the session fresh with the plan copied in, the scratch folder is gone, the task is `IN_PROGRESS`

#### Scenario: A key jagt.yml lacks
- **WHEN** the plan lists `- [changed] billing`, not configured
- **THEN** nothing is cut, the task stays `PLAN_PENDING` and the question names `billing` and the add-project form

#### Scenario: The Master approves
- **WHEN** the Master in `act` approves that plan
- **THEN** no project is added, nothing is cut, and the session is told to revise the plan: `billing` and the registered projects

#### Scenario: A cut fails
- **WHEN** the second worktree's cut fails
- **THEN** the first is removed and the task keeps its scratch folder

