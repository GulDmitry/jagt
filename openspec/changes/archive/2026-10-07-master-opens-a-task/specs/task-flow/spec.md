## MODIFIED Requirements

### Requirement: A worktree carries what the project needs
`worktree.copyGlobs` SHALL copy gitignored files (`.env`, key, cert) into every new worktree at the same path. A
repository's own `CLAUDE.md`, `AGENTS.md`, Codex config and git hooks MUST stay untouched and in force, jagt's
briefing and guard beside them. A push of any branch but the task's MUST be refused before anything leaves the machine.

#### Scenario: The agent pushes a foreign branch
- **WHEN** it pushes another branch
- **THEN** nothing leaves the machine, and the refusal tells it another branch is another task, asked for
