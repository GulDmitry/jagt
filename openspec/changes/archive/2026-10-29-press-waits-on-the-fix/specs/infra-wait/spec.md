## MODIFIED Requirements

### Requirement: A deploy stands on the pipeline jagt reads on its commit

#### Scenario: A press before the fix
- **WHEN** a `deploy` is pressed on ABC-42 while nothing is staged in its fix worktree
- **THEN** it is refused, saying the deploy waits on the session's fix in that worktree
- **AND** the worktree and the status stay
