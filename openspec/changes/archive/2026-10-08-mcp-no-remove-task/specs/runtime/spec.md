## MODIFIED Requirements

### Requirement: Closing the viewer only detaches
Closing the viewer SHALL only detach; killing SHALL be explicit — `done` or `close_task_tab`.

#### Scenario: The human closes the kitty window
- **WHEN** the viewer window is closed
- **THEN** the session keeps running until `done` or `close_task_tab`

### Requirement: jagt reaps a worktree's processes
Each sub-agent spawns its own language server (jdtls ~1–2 GB per Java worktree), never released. jagt SHALL reap
it on `done` (`port/WorktreeProcesses`, `LsofWorktreeProcesses.reap`: `lsof` by cwd, `kill -9`).
`orchestrator.agent-disabled-plugins` SHALL write `enabledPlugins: {"<name>": false}` into worktree settings,
default empty.

#### Scenario: A Java task is done
- **WHEN** `done` runs
- **THEN** every process whose cwd is in the worktree is killed
