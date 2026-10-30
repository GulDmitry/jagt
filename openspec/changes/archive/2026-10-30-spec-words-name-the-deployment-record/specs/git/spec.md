## MODIFIED Requirements

### Requirement: Revert refuses rather than guess
`revert` SHALL take out the last deploy's merge wherever the deployment record holds a merge, DEPLOYED and DEPLOY_CONFLICT
always; it refuses with a by-hand recipe where that is absent, reverted or conflicts. It takes out the fixes committed
on top (the target's recorded fixes) first, newest first, in the same push. It walks back the merged
repositories, each **forgetting** its commit, then discards a DEPLOY_CONFLICT's half-merge, sought only where
`jagt.yml` still names the project; REVERTED once all that landed is out.

#### Scenario: Deployed twice
- **WHEN** `revert <task>` after several deploys
- **THEN** only the last; earlier ones by hand: `git log --merges --grep ABC-42`, `git revert -m 1 <sha>`

#### Scenario: Revert from a conflict with nothing landed
- **WHEN** `revert` from DEPLOY_CONFLICT, no commit recorded
- **THEN** the half-merge is discarded; REVERTED

#### Scenario: Part-way revert
- **WHEN** `revert` stops part way
- **THEN** **stamped on the task**: DEPLOYED, or from DEPLOY_CONFLICT still DEPLOY_CONFLICT, its half-merge waiting
