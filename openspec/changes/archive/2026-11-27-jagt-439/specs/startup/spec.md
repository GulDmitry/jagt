## MODIFIED Requirements

### Requirement: One data folder holds everything jagt writes
jagt SHALL write `jagt/` (state and marks), `projects/`, `tasks/<task>/` (its worktrees, deploy and revert ones included),
`archive/`, `records/`, `memory/` and `logs/` under one data folder.
The folder is `orchestrator.data`, default `~/.jagt/data`, outside any checkout, overridden by `JAGT__orchestrator__data` or `ORCHESTRATOR_DATA`.
`jagt.yml` stays outside it. A project's `path` is used in place; a task in `state.json` keeps its recorded worktree.
#### Scenario: No data key
- **WHEN** jagt starts with no `orchestrator.data` and no `data/jagt/state.json` in the root
- **THEN** state, records, tasks, clones and logs are written under `~/.jagt/data` and nowhere else
#### Scenario: A data folder kept in the root
- **WHEN** jagt starts with no `orchestrator.data` and the root's `data/jagt/state.json` exists
- **THEN** that folder is used, never moved, and startup logs once its path, `orchestrator.data` and the command moving it
#### Scenario: A data folder inside a checkout
- **WHEN** jagt starts with a data folder inside a git checkout holding `AGENTS.md` or `CLAUDE.md`
- **THEN** startup names the path and `orchestrator.data` and goes on
#### Scenario: A data key
- **WHEN** `orchestrator.data` or `JAGT__orchestrator__data` names a folder
- **THEN** everything is written there
#### Scenario: A repository elsewhere
- **WHEN** a project's path lies outside the data folder
- **THEN** it is used in place and a new task's worktree is cut under `tasks/<task>/worktrees/`
#### Scenario: A repository in a read-only folder
- **WHEN** a project's path lies in a folder jagt cannot write in
- **THEN** jagt starts, and a deploy or revert cuts its worktree under `tasks/<task>/worktrees/`
#### Scenario: A state from an older version
- **WHEN** the root, `~/.jagt` or the data folder itself holds the `state.json` of an older version
- **THEN** that folder alone moves, logged per file; a kept name is warned, its master token deleted, a moved clone's worktrees repaired
#### Scenario: A data folder that cannot be written
- **WHEN** the data folder is a file or read-only
- **THEN** the start is refused naming `orchestrator.data`
