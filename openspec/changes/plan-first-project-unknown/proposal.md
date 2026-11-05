## Why
A human who knows what to do but not in which repository has to guess a project before the plan exists.

## What Changes
- The form starts a task with `plan first` and no project ticked; it plans in a scratch folder and is placed by its approved plan.
- The plan names its repositories under `## Repositories`; `approve-plan` cuts a worktree per `[changed]` key.
- A key `jagt.yml` lacks leaves the task at `PLAN_PENDING` with a question pointing at the add-project form.
