## MODIFIED Requirements

### Requirement: The worktree is trusted
`wireAgent` SHALL trust the worktree, replacing only `projects["<worktree>"].hasTrustDialogAccepted` in
`~/.claude.json` (`$CLAUDE_CONFIG_DIR/.claude.json` where set), under an OS file lock every jagt process takes.

#### Scenario: The config file is unparsable or the task is removed
- **WHEN** `~/.claude.json` cannot be parsed, or the task is removed
- **THEN** the file is left alone, or the worktree's entry is dropped

#### Scenario: Several jagt processes trust at once
- **WHEN** another jagt process is writing the config
- **THEN** this one waits for it, so neither flag is lost
