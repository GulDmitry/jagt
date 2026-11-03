## Why

A plan sent back after an approval is read whole again; the reader cannot see what the agent changed.

## What Changes

- Every `approve-plan` keeps the approved `plan.md` in jagt's own root as the task's baseline.
- The Artifacts tab shows the plan with the lines new or changed since that baseline bold with a bar, and the removed ones struck through.
- No plan approved yet: no marks.

## Capabilities

### Modified Capabilities

- `board`: the Artifacts tab marks a plan against the last approved one.
