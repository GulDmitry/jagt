## REMOVED Requirements

### Requirement: Revert refuses rather than guess
**Reason**: Split into shorter requirements, every fact kept.
**Migration**: None.

## ADDED Requirements

### Requirement: Revert takes out the last deploy or refuses
`revert` SHALL take out the last deploy's merge wherever the deployment record holds a merge, DEPLOYED and DEPLOY_CONFLICT
always; where that is absent, already reverted or not on the branch it refuses and picks nothing. It takes out the fixes committed
on top (the target's recorded fixes) first, newest first, then the merge, in one push.

#### Scenario: Deployed twice
- **WHEN** `revert <task>` after several deploys
- **THEN** only the last; an earlier one is picked with `revert <task> <sha>` from the candidates

#### Scenario: Deploy branch moved since the deploy
- **WHEN** `deployBranch` of proj changed after ABC-42 deployed
- **THEN** revert takes the merge out of the branch the record holds

### Requirement: A revert with no recorded merge lists its candidates
Where the record holds no merge, the refusal SHALL list the candidates: the merge commits on each deploy branch naming the task,
read from git, newest first, at most 10; `revert <task> <sha>` takes the one the human picks out.

#### Scenario: No merge recorded
- **WHEN** `revert ABC-42` and its record holds no merge
- **THEN** it is refused, listing each candidate as `<short sha> <date> <subject>` newest first, at most 10, per repository
- **AND** `revert ABC-42 <sha>` of one listed reverts that merge with `-m 1` and pushes; any other sha is refused
