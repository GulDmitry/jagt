## MODIFIED Requirements

### Requirement: The acceptance check runs the flows its read names
After an acceptance read, jagt SHALL run the flows the read named, each with its criterion, on the environment it named, in the deployed worktree.
With `flows.driver` set, the accept prompt names the flows repository, asking for each flow's id (`@AC-n` tag, else title) and the criterion it checks.
A flow naming no criterion is one row of the verdict and of `acceptance-report.md`: `passed` is met, `failed` not met, `unprovable` unprovable.
A flow naming one decides that criterion's row, as [`acceptance`](../acceptance/spec.md) says.
A read naming no flow runs nothing and adds no row.

#### Scenario: A flow fails
- **WHEN** the read, told the flows repository, answers `flows: [{id: AC-1, criterion: ""}]` on `https://alpha.test` and that flow fails
- **THEN** the verdict holds a not met row `AC-1` and `acceptance-report.md` carries it

#### Scenario: No flow named
- **WHEN** the read names no flow
- **THEN** no run starts and the report holds the read's rows alone
