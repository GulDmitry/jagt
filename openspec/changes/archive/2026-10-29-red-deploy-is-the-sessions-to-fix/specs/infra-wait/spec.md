## MODIFIED Requirements

### Requirement: A deploy stands on the pipeline jagt reads on its commit
A pipeline red on the code SHALL hand the deploy worktree to the session as a deploy conflict is handed: `DEPLOY_CONFLICT`, cut from the deploy branch as it stands. A fix staged there SHALL be committed and pushed by the next `deploy`, never by the session; the pipeline is then read on the commit it pushed.

#### Scenario: The deploy pipeline is red on the code
- **WHEN** a failed job of the pipeline on ABC-42's deploy commit failed its script
- **THEN** ABC-42 is `DEPLOY_CONFLICT`, its line reads `red pipeline on the deploy commit: proj 01234567`, and its deploy worktree is cut from `dev`
- **AND** where the Master acts the session is handed that worktree once; in `judge` the card says NEEDS YOU and nothing is pressed

#### Scenario: The session fixes the merge result
- **WHEN** the session stages its fix in full in the deploy worktree
- **THEN** jagt commits it and pushes it to `dev` through `deploy`, and ABC-42 is `DEPLOYED` on the new commit
- **AND** the pipeline on that commit is read, and the session is asked once it is green

#### Scenario: The deploy worktree cannot be opened
- **WHEN** the pipeline is red on the code and the worktree path holds a checkout jagt did not cut
- **THEN** the deploy stays open, its line says why, and the next read tries again
