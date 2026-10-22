## Why

A session in the CLI's plan mode could write nothing but its plan file and left only through an approval dialog in its window,
which neither the board nor the Master can reach. Every file jagt asks it to write became a prompt nobody saw.

## What Changes

- jagt launches no session in the CLI's plan mode; the plan gate is a fact of the task (`TaskState.planGate`).
- While it is closed, `/api/agent/tool` refuses a write to any file but `plan.md`, `task_visual.md` and `task_notes.md`.
- `approve-plan` at `PLAN_PENDING`, and the Master's `go`, open it and tell the session to start.
- `outcome=no_plan` lets an autoplan session open it; a `plan`-mode task refuses it.
- The plan lists every file the session will create after approval.

## Capabilities

### New Capabilities

### Modified Capabilities

- `task-flow`: the plan gate and the `approve-plan` verb.
- `runtime`: the gate hook also holds file writes; no CLI plan mode.
- `protocol`: the `no_plan` outcome.
- `spec-changes`: a plan lists the change instead of being unable to write it.
- `master`: `go` approves as `approve-plan` does.
- `board`: the verb, and what it replaces; one scenario for the two sightings.
