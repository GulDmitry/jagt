## ADDED Requirements

### Requirement: A plan shows what changed since its last approval
Each `approve-plan` SHALL keep the approved `plan.md` as the task's baseline, in jagt's own root, not the worktree.
The Artifacts tab SHALL render `plan.md` against that baseline: a line new or changed is bold with a bar,
a removed line struck through in the muted colour where it stood. No approved plan, no marks. No other document is marked.
#### Scenario: No plan approved yet
- **WHEN** ABC-42's plan is open in the Artifacts tab and no `approve-plan` ran
- **THEN** no line is marked and none is struck

#### Scenario: Changed after the approval
- **WHEN** ABC-42's plan was approved, then a line was rewritten and another deleted
- **THEN** the rewritten line is marked, the deleted one is shown struck through, and every other line is plain

#### Scenario: Approved again
- **WHEN** the changed plan is approved
- **THEN** it becomes the baseline and the next read of the plan marks nothing
