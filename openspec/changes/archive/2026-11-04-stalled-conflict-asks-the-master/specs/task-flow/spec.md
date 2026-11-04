## MODIFIED Requirements

### Requirement: A stalled deploy conflict is the human's
A handed conflict unchanged for `conflictStallMinutes` SHALL go to the Master first where it answers:
`act`, `answer` not kept by the human, task not stopped, no question standing. The stall becomes the session's `QUESTION` at `DEPLOY_CONFLICT`,
answered by the Master as any question is.
The `Conflict` SHALL record that it was asked, and the stall clock restarts then.
A stall after that, or one where the Master does not answer, SHALL tell the human once. Its card SHALL name the paths unmerged,
where and since when, across a restart (its `Conflict`, [0063](../../../../docs/decisions/0063-a-stalled-deploy-conflict-is-the-humans.md),
[0134](../../../../docs/decisions/0134-a-stalled-deploy-conflict-asks-the-master-first.md)); a status past it clears it.

#### Scenario: A conflict nobody works, where the Master answers
- **WHEN** ABC-42's deploy worktree has not changed for `conflictStallMinutes` and the Master acts
- **THEN** no banner: ABC-42's report is a `QUESTION` naming the paths unmerged, its worktree and since when, and the Master answers it
- **AND** the answer is relayed to the same session, and a restart in between asks nothing again

#### Scenario: The Master's question goes unanswered or the worktree stays unchanged
- **WHEN** the worktree has not changed for `conflictStallMinutes` since the Master was asked
- **THEN** one banner, and the card says NEEDS YOU with its unmerged paths, its worktree and since when

#### Scenario: A conflict nobody works
- **WHEN** ABC-42's deploy worktree has not changed for `conflictStallMinutes` and the Master does not answer (`judge`)
- **THEN** one banner, and the card says NEEDS YOU with its unmerged paths, its worktree and since when
