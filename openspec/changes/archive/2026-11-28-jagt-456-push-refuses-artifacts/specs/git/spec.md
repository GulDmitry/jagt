## MODIFIED Requirements

### Requirement: Push guard lives in the worktree
`.jagt/hooks/pre-push` (`WorktreeHooks`, the refs) SHALL refuse a push not to the task's branch or carrying an `Artifact` at the root.
`WorktreeHooks.gitEnv` sets `core.hooksPath` via `GIT_CONFIG_*` on the launch command: that session and children, no repository config.
A guardrail: a runtime-built push, an alias or a script passes; `deploy`, `revert` and a human's shell run ungated.

#### Scenario: Hooks
- **WHEN** a client-side hook fires **THEN** a stub runs the repository's own, re-resolved at run time with the override off, guard first
