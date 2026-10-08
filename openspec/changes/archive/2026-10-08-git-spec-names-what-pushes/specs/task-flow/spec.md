## MODIFIED Requirements

### Requirement: A worktree carries what the project needs
`worktree.copyGlobs` SHALL copy gitignored files (`.env`, key, cert) into every new worktree at the same path. A
repository's own `CLAUDE.md`, `AGENTS.md`, Codex config and git hooks MUST stay untouched and in force, jagt's
briefing and guard beside them. A plain push of another branch MUST be refused before it leaves: a guardrail, not a
boundary.

#### Scenario: The agent pushes a foreign branch
- **WHEN** it pushes another branch
- **THEN** nothing leaves the machine; the refusal says: ask for another task
