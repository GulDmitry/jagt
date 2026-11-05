## MODIFIED Requirements

### Requirement: What jagt revives it re-enters

#### Scenario: The tmux server is lost, a relay comes late, or a round comes early
- **WHEN** jagt revives a session whose worktree holds no conversation (its repository left the task)
- **THEN** the session starts fresh, since `--continue` there exits
