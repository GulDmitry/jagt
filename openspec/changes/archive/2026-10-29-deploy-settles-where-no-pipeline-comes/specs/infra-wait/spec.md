## MODIFIED Requirements

### Requirement: A deploy stands on the pipeline jagt reads on its commit
A deploy SHALL stand with no pipeline where none can come: the host lists none for `NO_PIPELINE_AFTER` (10 minutes) after the push, or the repository has no code-host address. A host that does not answer keeps it open until a read succeeds.

#### Scenario: No pipeline will come
- **WHEN** the host lists no pipeline for ABC-42's deploy commit 10 minutes after the push, or its repository's remote is a local path
- **THEN** the deploy stands and its session is asked once; within the 10 minutes the line says the pipeline is not started
- **AND** one INFO says `deploy settled without a pipeline`, naming the cause
