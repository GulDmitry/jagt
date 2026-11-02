## MODIFIED Requirements

### Requirement: Only deploy and revert write shared branches
`deploy` SHALL merge `origin/<task>` into `deployBranch` (`mergeIntoAndPush`), `revert` its merge commit
(`revertMergeAndPush`); Master-only (`Audience.MASTER`). `do <ticket> from <branch>` (`TaskState.baseBranch`) moves
the cut and the request's **target**, never the merge destination (`baseBranchOr`). A `deployBranch` naming
`baseBranch`, however spelled, MUST be refused.
With `remote: none` both fast-forward the checkout holding the target, which git refuses a push to, else push within the repository.

#### Scenario: Diff base
- **WHEN** `ide <task> diff` shows files the task never touched
- **THEN** read against the request's target, never `deployBranch`
