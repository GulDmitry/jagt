## ADDED Requirements

### Requirement: A session may say only the human can unblock it
`update_agent_status` SHALL take `outcome=permission`: a question stopped on a call the CLI denied, written
`awaiting permission` into the line, which nothing standing in for the human answers ([master](../master/spec.md)).

#### Scenario: Edits denied in the deploy worktree
- **WHEN** a session reports `outcome=permission`
- **THEN** the card reads `NEEDS INPUT: permission: …` and the Master leaves it to you
