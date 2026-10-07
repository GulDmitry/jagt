## MODIFIED Requirements

### Requirement: Every call a session needs is pre-approved
Claude Code's auto-mode classifier silently blocks calls not pre-approved: the backend SHALL make none, and the
committed root `.claude/settings.json` covers sessions there. Every sub-agent worktree's generated
`.claude/settings.local.json` MUST carry `enableAllProjectMcpServers: true` plus
`permissions.allow: ["mcp__jagt-orchestrator", "Bash(git:*)"]` and every tool in `agentAllowedTools`. Shared
branches are guarded by the detached upstream and prompt rules, not this allow-list.

#### Scenario: A worktree setting is missing
- **WHEN** a worktree lacks `enableAllProjectMcpServers`, or lacks the allow-list
- **THEN** `ship` / `feedback` stall on an invisible prompt, or `git commit` freezes

#### Scenario: The generated settings change
- **WHEN** they change while a worktree already exists
- **THEN** it keeps its old file until patched or re-created: only `initialize_task` writes it

#### Scenario: The classifier refuses a ship's request
- **WHEN** the code host's request write is not in `agentAllowedTools`
- **THEN** the classifier may refuse it; listed, it is pre-approved
