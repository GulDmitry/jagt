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

### Requirement: A scratch folder is no repository
A task with no repository SHALL hold no worktree, branch or request: its folder under `scratch/` is outside git for its session
and `ship` and `deploy` are not offered.

#### Scenario: A plan-only task
- **WHEN** a task holds only its scratch folder
- **THEN** a push, `ship` and `deploy` are unavailable and `scratch/` is untracked

### Requirement: A task with no repository plans its repositories
The brief of a task with no repository SHALL tell the session it plans in a scratch folder.
It SHALL say the plan's `## Repositories` names every project the task changes, configured or not.

#### Scenario: A plan-first session with no project
- **WHEN** such a task's session is briefed
- **THEN** it reads that `plan.md` needs `## Repositories` with a `- [changed] <key>` per project
