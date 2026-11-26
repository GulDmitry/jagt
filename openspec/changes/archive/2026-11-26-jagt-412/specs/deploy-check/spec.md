## ADDED Requirements

### Requirement: A deploy is decided before its session leaves it
A session's report moving a `DEPLOYED` task elsewhere SHALL be refused, the task staying `DEPLOYED`, until the pipeline on
each landed tip read green and no checker reads the deploy. A new tip owes its own green read.

#### Scenario: The session leaves before the pipeline is read
- **WHEN** ABC-42 is `DEPLOYED`, its pipeline running, and its session reports `IN_PROGRESS`
- **THEN** the report is refused naming the undecided deploy and ABC-42 stays `DEPLOYED`
- **AND** a red pipeline then hands the session the deploy worktree as `DEPLOY_CONFLICT`

#### Scenario: Acceptance still reads the deploy
- **WHEN** ABC-42's pipeline read green and acceptance reads it, and its session reports `IN_PROGRESS`
- **THEN** the report is refused and ABC-42 stays `DEPLOYED`

### Requirement: A press keeps what a live deploy recorded
A new press SHALL carry a live target's push time and pipeline reads; only a new landing replaces them.

#### Scenario: A press after a deploy
- **WHEN** ABC-42's merge was pushed to `dev` and `deploy` is pressed again
- **THEN** the target keeps its recorded push time

## MODIFIED Requirements

### Requirement: A deploy pipeline red on the code goes to the session
A pipeline red on the code SHALL be re-run once per commit jagt pushed (`DeployPipelines`), kept on the deployment's target across a restart
([0133](../../../docs/decisions/0133-a-red-deploy-pipeline-is-rerun-once.md)); the deploy stays open meanwhile. Red after its re-run, or refused
one, it SHALL hand the session the deploy worktree: `DEPLOY_CONFLICT`, handed as any deploy conflict is
([task-flow](../task-flow/spec.md#requirement-in-act-a-deploy-conflict-is-the-sessions)), its line naming each failed job read and its reason.

#### Scenario: The deploy pipeline is red on the code
- **WHEN** a failed job of the pipeline on ABC-42's deploy commit failed its script
- **THEN** its failed jobs are re-run once and the deploy stays open
- **AND** a restart in between re-runs nothing again

#### Scenario: The re-run is red again
- **WHEN** the pipeline on ABC-42's deploy commit is red again after its re-run, job `verifyBuild` failing its script
- **THEN** ABC-42 is `DEPLOY_CONFLICT`, its line reads `red pipeline on the deploy commit: proj 01234567, failed verifyBuild (script_failure)`,
  its worktree cut from `dev` as it stands
- **AND** where the Master acts the session is handed that worktree once; in `judge` the card says NEEDS YOU and nothing is pressed

#### Scenario: The host refuses the re-run
- **WHEN** the host refuses to re-run the pipeline
- **THEN** the red is handed back as it stands, with no second try

#### Scenario: The session fixes the merge result
- **WHEN** the session stages its fix in full in the deploy worktree
- **THEN** the next `deploy` commits it and pushes it to `dev`, never the session, and ABC-42 is `DEPLOYED` on the new commit
- **AND** the pipeline is read on the commit pushed, re-run once if red on the code, and the session is asked once it is green

#### Scenario: A press before the fix
- **WHEN** a `deploy` is pressed on ABC-42 while nothing is staged in its fix worktree
- **THEN** it is refused, saying the deploy waits on the session's fix in that worktree
- **AND** the worktree and the status stay

#### Scenario: The deploy worktree cannot be opened
- **WHEN** the pipeline is red on the code after its re-run and the worktree path holds a checkout jagt did not cut
- **THEN** the deploy stays open, its line says why, and the next read tries again
