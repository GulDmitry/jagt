## MODIFIED Requirements

### Requirement: Revert refuses rather than guess
`revert` SHALL take out the last deploy's merge wherever a `deployCommit` is recorded, DEPLOYED and DEPLOY_CONFLICT
always; it refuses with a by-hand recipe where that is absent, reverted or conflicts. It walks back the merged
repositories, each **forgetting** its commit, then discards a DEPLOY_CONFLICT's half-merge, sought only where
`jagt.yml` still names the project; REVERTED once all that landed is out. Part way, **stamped on the task**, it leaves
DEPLOYED, or DEPLOY_CONFLICT where it came from.

#### Scenario: Deployed twice
- **WHEN** `revert <task>` after several deploys
- **THEN** only the last; earlier ones by hand: `git log --merges --grep ABC-42`, `git revert -m 1 <sha>`

#### Scenario: Revert from a conflict with nothing landed
- **WHEN** `revert` from DEPLOY_CONFLICT, no commit recorded
- **THEN** the half-merge is discarded; REVERTED

#### Scenario: Part-way revert from a conflict
- **WHEN** `revert` from DEPLOY_CONFLICT stops part way
- **THEN** DEPLOY_CONFLICT: the half-merge still waits
