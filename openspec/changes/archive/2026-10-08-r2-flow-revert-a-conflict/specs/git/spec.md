## MODIFIED Requirements

### Requirement: Revert refuses rather than guess
`revert` SHALL take out the last deploy's merge wherever a `deployCommit` is recorded, DEPLOYED and DEPLOY_CONFLICT
always; it refuses with a by-hand recipe on commit absent, already reverted, conflict, or none recorded — where a
DEPLOY_CONFLICT only discards its half-merge. It walks back the merged repositories, each **forgetting** its commit, and
only then discards that half-merge; REVERTED once all that landed is out, both half-states **stamped on the task**.

#### Scenario: Deployed twice
- **WHEN** `revert <task>` after several deploys
- **THEN** only the last; earlier ones by hand: `git log --merges --grep ABC-42`, `git revert -m 1 <sha>`
