## MODIFIED Requirements

### Requirement: A session judges each switch that is on
A session SHALL answer each of `autoplan`, `visualize` and `diagnose` that is on as `yes — <why>` or `no — <why>`.
The answers go in `switches` (`protocol/SwitchJudgements`) of its first `update_agent_status` report, whatever status it reports.
They go in the report handing back its plan at `PLAN_PENDING` too, judged after reading the task.
jagt SHALL refuse such a report as `validation`, naming the switch and the form, when an answer is missing, lacks its why or is
unknown; other reports need none and a later answer replaces an earlier.
jagt SHALL refuse a plan hand-back answering `visualize` with `yes` as `validation` naming `task_visual.md`, while the file is missing.

#### Scenario: A switch left unanswered
- **WHEN** `visualize` is on and the first report of ABC-42 carries no `switches` answer for it
- **THEN** the report is refused as `validation` naming `visualize`, and the task stays NEW

#### Scenario: A plan handed back without an answer
- **WHEN** ABC-42 hands back its plan with `visualize` on and no `switches` answer for it
- **THEN** the report is refused as `validation` naming `visualize`, and the task stays where it was

#### Scenario: A yes without its picture
- **WHEN** ABC-42 hands back its plan answering `visualize` with `yes — <why>` and its worktree holds no `task_visual.md`
- **THEN** the report is refused as `validation` naming `task_visual.md`, and the task stays where it was

#### Scenario: A switch that is off
- **WHEN** `visualize` is off and the first report of ABC-42 carries no answer for it
- **THEN** the report is accepted

### Requirement: A task worth a picture gets task_visual.md before its first edit
When it is, the session SHALL
write `task_visual.md` in its worktree with its plan when it plans first, else before its first edit:
a frontend change as an ASCII sketch of each screen, one fenced block per screen;
a backend change as Mermaid and markdown tables, an ER diagram drawing every reference between the tables,
a sequence diagram every call; logic as a worked example, its input and its outcome. jagt draws and stores nothing.

#### Scenario: A task worth a picture
- **WHEN** ABC-42 changes a screen and visualize is on
- **THEN** the session writes `task_visual.md` with one fenced block per screen before its first edit

#### Scenario: New tables
- **WHEN** ABC-42 adds two tables, one referencing the other, and visualize is on
- **THEN** `task_visual.md` holds an ER diagram drawing that reference

#### Scenario: A call between services
- **WHEN** ABC-42 makes one service call another and visualize is on
- **THEN** `task_visual.md` holds a sequence diagram of that call
