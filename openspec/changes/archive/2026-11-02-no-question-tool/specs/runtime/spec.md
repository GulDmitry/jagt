## MODIFIED Requirements

### Requirement: The session compacts at 300k tokens
A Claude session SHALL compact at 300k tokens (`ClaudeAgentRuntime.COMPACT_AT_TOKENS`), set on the command too. Its
settings SHALL carry `autoMemoryEnabled: false` and `idleCompaction: false`: idle counts from the turn's end.
They SHALL deny `AskUserQuestion`, so no dialog stands between a relayed answer and the session.

#### Scenario: Worktree settings predate the limit
- **WHEN** older settings lack the limit
- **THEN** the command still sets it; the question dialog stays open there until the worktree is re-created

