## MODIFIED Requirements

### Requirement: Ship hands the work to the agent
A request is merged when the host says so in a review read, which stands: git alone never sees a squash. Otherwise git decides: a merge commit of the
target took the branch tip in (`mergedIntoTarget`, after a fetch; a failed fetch is no answer). `MergedReadJob` asks again every 5 minutes.

#### Scenario: Ship
- **WHEN** `ship ABC-42`
- **THEN** a commit, push and request per repository onto its base
- **AND** with !571 merged, nothing is shipped and no SHIPPING step is recorded; a squash counts by the host's word, a never-diverged branch not
- **AND** once git no longer finds !571's tip in the target and the host never said it merged, `ship` is allowed and auto-review polls again
