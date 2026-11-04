## MODIFIED Requirements

### Requirement: A deploy pipeline red on the code goes to the session
A pipeline red on the code SHALL be re-run once per commit jagt pushed (`DeployPipelines`), kept on the deployment's target across a restart
([0133](../../../../docs/decisions/0133-a-red-deploy-pipeline-is-rerun-once.md)); the deploy stays open meanwhile.
A pipeline still red after its re-run, or one the host refuses to re-run, SHALL hand the deploy worktree to the session as a deploy conflict is handed
([task-flow](../task-flow/spec.md#requirement-in-act-a-deploy-conflict-is-the-sessions)): `DEPLOY_CONFLICT`, cut from the deploy branch as it stands.
A fix staged there SHALL be committed and pushed by the next `deploy`, never by the session; the pipeline is then read on the commit it pushed.

#### Scenario: The deploy pipeline is red on the code
- **WHEN** a failed job of the pipeline on ABC-42's deploy commit failed its script
- **THEN** its failed jobs are re-run once and the deploy stays open
- **AND** a restart in between re-runs nothing again

#### Scenario: The re-run is red again
- **WHEN** the pipeline on ABC-42's deploy commit is red again after its re-run
- **THEN** ABC-42 is `DEPLOY_CONFLICT`, its line reads `red pipeline on the deploy commit: proj 01234567`, and its deploy worktree is cut from `dev`
- **AND** where the Master acts the session is handed that worktree once; in `judge` the card says NEEDS YOU and nothing is pressed

#### Scenario: The host refuses the re-run
- **WHEN** the host refuses to re-run the pipeline
- **THEN** the red is handed back as it stands, with no second try

#### Scenario: A fix is pushed after the re-run
- **WHEN** the session's fix is pushed and its pipeline goes red
- **THEN** that commit's pipeline is re-run once in turn

#### Scenario: The session fixes the merge result
- **WHEN** the session stages its fix in full in the deploy worktree
- **THEN** jagt commits it and pushes it to `dev` through `deploy`, and ABC-42 is `DEPLOYED` on the new commit
- **AND** the pipeline on that commit is read, and the session is asked once it is green

#### Scenario: A press before the fix
- **WHEN** a `deploy` is pressed on ABC-42 while nothing is staged in its fix worktree
- **THEN** it is refused, saying the deploy waits on the session's fix in that worktree
- **AND** the worktree and the status stay

#### Scenario: The deploy worktree cannot be opened
- **WHEN** the pipeline is red on the code after its re-run and the worktree path holds a checkout jagt did not cut
- **THEN** the deploy stays open, its line says why, and the next read tries again
