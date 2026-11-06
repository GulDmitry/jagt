## MODIFIED Requirements

### Requirement: jagt reaps a worktree's processes
Each sub-agent spawns its own language server (jdtls ~1–2 GB per Java worktree), never released. jagt SHALL reap
it on `done` (`port/WorktreeProcesses`, `LsofWorktreeProcesses.reap`).
`orchestrator.claude.disabledPlugins` SHALL write `enabledPlugins: {"<name>": false}` into worktree settings, default empty.

#### Scenario: A Java task is done
- **WHEN** `done` runs
- **THEN** every process whose cwd is in the worktree is killed
