## ADDED Requirements

### Requirement: A Codex session's turns are booked unpriced
With `orchestrator.agent.cli=codex`, each `token_count` event of the session's newest rollout under the worktree's
Codex home that carries usage SHALL book one call: fresh input (input minus cached), cached input and output tokens.
No Codex price is known, so the call is unpriced. An agent CLI keeping no log jagt reads books nothing.

#### Scenario: A Codex turn
- **WHEN** the agent CLI is codex and its rollout holds a `token_count` turn with usage
- **THEN** its tokens are booked, cached input apart from fresh input, as one unpriced call

#### Scenario: A token count without usage
- **WHEN** a `token_count` event carries no usage
- **THEN** nothing is booked for it
