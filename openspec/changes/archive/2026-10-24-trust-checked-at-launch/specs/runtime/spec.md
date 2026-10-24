## MODIFIED Requirements

### Requirement: The worktree is trusted
`wireAgent` SHALL trust the worktree, replacing only `projects["<worktree>"].hasTrustDialogAccepted` in
`~/.claude.json` (`$CLAUDE_CONFIG_DIR/.claude.json` where set), under an OS file lock every jagt process takes.
`AgentRuntime.beforeLaunch` SHALL check it right before each launch line, writing a lost flag again up to three times.

#### Scenario: The config file is unparsable, the task is removed, or another jagt process writes
- **WHEN** `~/.claude.json` cannot be parsed, the task is removed, or another jagt process is writing it
- **THEN** the file is left alone, the worktree's entry is dropped, or this one waits, so no flag is lost
- **WHEN** the CLI dropped the flag between provisioning and launch
- **THEN** it is written again before the session starts
