## MODIFIED Requirements

### Requirement: Pipeline status keeps the host's wording
`TaskState.pipelineStatus` SHALL keep the host's own wording, `flow/Pipeline` its one parser → GREEN, RED, RUNNING, NONE (no
pipeline), UNKNOWN (nobody read one). A round reading no listing SHALL write nothing, the last word standing flagged. The
third such round running since the `lift` SHALL stop the task, its evidence the reads' words for why; a listing counts anew.

#### Scenario: No checks dot while the host shows a failed run
- **WHEN** no checks dot shows while the host shows a failed run
- **THEN** nobody read one yet; `none` means the host listed none

#### Scenario: Checks nobody can list
- **WHEN** a third poll running cannot list ABC-42's pipelines, `list_pipelines: 403 Forbidden`
- **THEN** jagt stops it, and the hover says `3 reads running could not list the checks: list_pipelines: 403 Forbidden`
