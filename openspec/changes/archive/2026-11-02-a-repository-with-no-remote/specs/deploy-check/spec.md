## MODIFIED Requirements

### Requirement: A deploy stands with no pipeline where none can come
A deploy SHALL stand with no pipeline where none can come.
That is: the host lists none for `NO_PIPELINE_AFTER` (10 minutes) after the repository's recorded push, the repository has no
code-host address, or its `codeHost` is `none`. A `remote` of `none` implies that last one.
A target that stood on the first SHALL be read on while `DEPLOYED`.

#### Scenario: No pipeline will come
- **WHEN** the host lists no pipeline for ABC-42's deploy commit 10 minutes after its recorded push
- **OR** its repository's remote is a local path, or its `codeHost` is `none`
- **THEN** the deploy stands and its session is asked once; within the 10 minutes the line says the pipeline is not started
- **AND** one INFO says `deploy settled without a pipeline`, naming the cause
- **AND** a pipeline the host lists later is read while ABC-42 is `DEPLOYED`; red on the code makes it `DEPLOY_CONFLICT`
