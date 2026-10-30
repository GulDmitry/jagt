# revert Specification

## Purpose

The undo of a deploy, and the conflict it can leave.

## Requirements

### Requirement: Revert refuses rather than guess
`revert` SHALL take out the last deploy's merge wherever the deployment record holds a merge, DEPLOYED and DEPLOY_CONFLICT
always; it refuses with a by-hand recipe where that is absent, already reverted or not on the branch. It takes out the fixes committed
on top (the target's recorded fixes) first, newest first, then the merge, in one push. It walks back the merged
repositories last first, each **forgetting** its commit, then discards a DEPLOY_CONFLICT's half-merge, sought only where
`jagt.yml` still names the project; REVERTED once all that landed is out.

#### Scenario: Deployed twice
- **WHEN** `revert <task>` after several deploys
- **THEN** only the last; earlier ones by hand: `git log --merges --grep ABC-42`, `git revert -m 1 <sha>`

#### Scenario: Deploy branch moved since the deploy
- **WHEN** `deployBranch` of proj changed after ABC-42 deployed
- **THEN** revert takes the merge out of the branch the record holds

#### Scenario: Revert from a conflict with nothing landed
- **WHEN** `revert` from DEPLOY_CONFLICT, no commit recorded
- **THEN** the half-merge is discarded; REVERTED

#### Scenario: Part-way revert
- **WHEN** `revert` stops part way
- **THEN** **stamped on the task**: DEPLOYED, or from DEPLOY_CONFLICT still DEPLOY_CONFLICT, its half-merge waiting

### Requirement: A revert that conflicts goes to the session
A revert that conflicts SHALL push nothing for that repository and LEAVE its revert worktree (`<task>-revert`) with the conflict:
DEPLOY_CONFLICT, its `Conflict` of cause `REVERT`, handed to the session as a deploy conflict is
([0114](../../../docs/decisions/0114-a-revert-conflict-goes-to-the-session.md)). The next `revert` continues it there and finishes the revert.

#### Scenario: A revert conflicts
- **WHEN** `revert` hits a conflict reverting a fix or the merge
- **THEN** nothing is pushed for it, its revert worktree keeps the conflict, and the task is DEPLOY_CONFLICT naming the worktree and both sides
- **AND** a repository reverted before it stays reverted and is not reverted again
- **AND** a restart finds it from the deployment record and the revert worktree

#### Scenario: A revert conflict resolved
- **WHEN** nothing is unmerged and `revert` is pressed
- **THEN** the revert completes, the target's remaining reverts run, it pushes, and the remaining repositories follow; REVERTED once all are out

#### Scenario: Refused at a revert conflict
- **WHEN** `revert` while paths are unmerged, or `deploy` where the conflict is a revert's
- **THEN** it is refused, the first naming the paths, nothing pushed or discarded
- **AND** `revert` replaces `deploy` as the card's primary button, no mark or legend row added
