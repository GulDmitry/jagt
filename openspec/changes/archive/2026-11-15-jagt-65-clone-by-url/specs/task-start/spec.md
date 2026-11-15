## ADDED Requirements

### Requirement: A worktree's local files come from the project's local folder
A project's `localFiles` SHALL name the folder `worktree.copyGlobs` copies from into every new worktree; blank, it is the project's path.
A clone is fetched before a task is cut from it.
#### Scenario: A clone's gitignored file
- **WHEN** a cloned project's `localFiles` holds `.env` and `worktree.copyGlobs` names `.env`
- **THEN** every new worktree has it
#### Scenario: A clone fetched before a task
- **WHEN** the url's repository gained a commit after the clone and a task starts
- **THEN** the task's branch is cut from the base including that commit
