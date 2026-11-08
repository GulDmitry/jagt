## MODIFIED Requirements

### Requirement: The QA check runs the flows its read names
After a QA read, jagt SHALL run the flow ids the read named on the environment it named, in the deployed worktree.
With `flows.driver` set, the accept prompt names the flows repository, asking for ids (`@AC-n` tags, else titles).
Each flow is one row of the verdict and of `qa-report.md`: `passed` is met, `failed` not met, `unprovable` unprovable.
A read naming no flow runs nothing and adds no row.

#### Scenario: A flow fails
- **WHEN** the read, told the flows repository, answers `flows: [AC-1]` on `https://alpha.test` and that flow fails
- **THEN** the verdict holds a not met row `AC-1` and `qa-report.md` carries it

#### Scenario: No flow named
- **WHEN** the read names no flow
- **THEN** no run starts and the report holds the read's rows alone
