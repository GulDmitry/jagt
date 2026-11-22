## MODIFIED Requirements

### Requirement: Only deploy and revert write shared branches
`deploy` SHALL merge `origin/<task>` into `deployBranch` (`mergeIntoAndPush`), `revert` its merge commit
(`revertMergeAndPush`); root-session-only (`Audience.ROOT`). `do <ticket> from <branch>` (`TaskState.baseBranch`) moves
the cut and the request's **target**, never the merge destination (`baseBranchOr`). A `deployBranch` naming
`baseBranch`, however spelled, MUST be refused. With `remote: none` both fast-forward the target's checkout
(git refuses a push to it), else push within the repository.

#### Scenario: Diff base
- **WHEN** `code <task> diff` shows files the task never touched
- **THEN** read against the request's target, never `deployBranch`
