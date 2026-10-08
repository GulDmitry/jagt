## MODIFIED Requirements

### Requirement: ToolGate refuses the line
`ToolGate` (`POST /api/agent/tool`) SHALL refuse that push too. It also refuses a push naming no branch, deleting the
task's (`--de`, `-ud`), or forcing it without the lease; `HEAD` unless bare and unmoved; `send-pack`, `http-push`,
`receive-pack` and every `gh`/`glab` command, however wrapped, but a read; any line naming the board's port or
the Master's token.

#### Scenario: Skipping the hook
- **WHEN** a push line could skip the hook: `--no-verify`, `git -c`, `core.hooksPath`, `alias.`, `GIT_CONFIG*`, `env -i`, `sh -c`, `eval`, however quoted or escaped; a git line's `$'…'`
- **THEN** refused

### Requirement: Done retires checkouts, never in bulk
`done` SHALL end the agent and delete every worktree and checkout the task cut; the branch survives. `prune all`
MUST be refused by name.

#### Scenario: Diff worktrees
- **WHEN** `done <task>` with `jagt-diff-*` worktrees
- **THEN** removed
