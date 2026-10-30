## MODIFIED Requirements

### Requirement: A deploy stands on the pipeline jagt reads on its commit
A deploy SHALL stand only once the host's pipeline on each repository's `deployCommit` is green.
`DeployCheckJob` reads it every minute through the host's own CLI, not a model, until the deploy is asked about
([0109](../../../docs/decisions/0109-a-deploys-outcome-is-the-pipeline-jagt-reads-on-its-commit.md)).
A pipeline running, not started, unknown or unreadable keeps the deploy open, and the task's line says `waiting on its pipeline`.
A deploy SHALL stand with no pipeline where none can come: the host lists none for `NO_PIPELINE_AFTER` (10 minutes) after the repository's
recorded push, or the repository has no code-host address. A host that does not answer keeps it open until a read succeeds.
A pipeline red on infrastructure SHALL be retried and counted as a request's is, and stops the task as `INFRA` after the last retry.
A pipeline red on the code SHALL hand the deploy worktree to the session as a deploy conflict is handed
([task-flow](../task-flow/spec.md#requirement-in-act-a-deploy-conflict-is-the-sessions)): `DEPLOY_CONFLICT`, cut from the deploy branch as it stands.
A fix staged there SHALL be committed and pushed by the next `deploy`, never by the session; the pipeline is then read on the commit it pushed.

#### Scenario: No pipeline will come
- **WHEN** the host lists no pipeline for ABC-42's deploy commit 10 minutes after its recorded push, or its repository's remote is a local path
- **THEN** the deploy stands and its session is asked once; within the 10 minutes the line says the pipeline is not started
- **AND** one INFO says `deploy settled without a pipeline`, naming the cause
