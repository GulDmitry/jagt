## MODIFIED Requirements

### Requirement: Push guard lives in the worktree
`.jagt/hooks/pre-push` (`WorktreeHooks`, the refs) SHALL refuse a push not to the task's branch. `ToolGate`
(`POST /api/agent/tool`, the command LINE) SHALL refuse that too, and: a push naming no branch or deleting the task's;
`HEAD` where the line may move it; a line that could skip the hook (`--no-verify`, `core.hooksPath`, `alias.`,
`GIT_CONFIG*`, `env -i`, `sh -c`, `eval`). `WorktreeHooks.gitEnv` sets `core.hooksPath` via `GIT_CONFIG_*` on
`TmuxSessionHost`'s launch command: that session and children, no repository config. A guardrail, not a boundary: a
push built at runtime, a configured alias or a script passes; `deploy`, `revert` and a human's shell run ungated.

#### Scenario: Hooks
- **WHEN** a client-side hook fires
- **THEN** a stub runs the repository's own, re-resolved at run time with the override off, guard first
