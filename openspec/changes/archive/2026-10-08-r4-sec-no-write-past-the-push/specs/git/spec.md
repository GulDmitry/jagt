## MODIFIED Requirements

### Requirement: Push guard lives in the worktree
`.jagt/hooks/pre-push` (`WorktreeHooks`, the refs) SHALL refuse a push not to the task's branch. `WorktreeHooks.gitEnv`
sets `core.hooksPath` via `GIT_CONFIG_*` on the launch command: that session and children, no repository config. A
guardrail: a runtime-built push, an alias or a script passes; `deploy`, `revert` and a human's shell run ungated.

#### Scenario: Hooks
- **WHEN** a client-side hook fires
- **THEN** a stub runs the repository's own, re-resolved at run time with the override off, guard first

### Requirement: ToolGate refuses the line
`ToolGate` (`POST /api/agent/tool`) SHALL refuse that push too. It also refuses a push naming no branch, deleting the
task's, or forcing it without the lease; `HEAD` where the line may move it; `send-pack`, `http-push`, `receive-pack`
and a non-GET `gh`/`glab api`; any line naming the board's port or the Master's token.

#### Scenario: Skipping the hook
- **WHEN** a line could skip the hook: `--no-verify`, `core.hooksPath`, `alias.`, `GIT_CONFIG*`, `env -i`, `sh -c`, `eval`, however quoted
- **THEN** refused
