## ADDED Requirements

### Requirement: One data folder holds everything jagt writes
jagt SHALL write `state.json` and its siblings, `worktrees/<project>/<task>`, `repos/<name>` and `logs/` under one data
folder, `orchestrator.data`, default `~/.jagt`, overridden by `JAGT__orchestrator__data` or `ORCHESTRATOR_DATA`.
`jagt.yml` stays outside it. A project's `path` is used in place; a task in `state.json` keeps its recorded worktree.
#### Scenario: No data key
- **WHEN** jagt starts on a desktop with no `orchestrator.data`
- **THEN** state, worktrees, clones and logs are written under `~/.jagt` and nowhere else
#### Scenario: A data key
- **WHEN** `orchestrator.data` or `JAGT__orchestrator__data` names a folder
- **THEN** everything is written there
#### Scenario: A repository elsewhere
- **WHEN** a project's path lies outside the data folder
- **THEN** it is used in place and a new task's worktree is cut under `worktrees/<project>/`
#### Scenario: A state from an older version
- **WHEN** a `state.json` sits in the root and the data folder holds none
- **THEN** it is moved into the data folder once, and its tasks resume with their recorded worktrees
#### Scenario: A data folder that cannot be written
- **WHEN** the data folder is a file or read-only
- **THEN** the start is refused naming `orchestrator.data`
