# 0059 — Plan first is jagt's gate, not the CLI's plan mode

[← decisions](README.md) · spec: [`task-flow`](../../openspec/specs/task-flow/spec.md#requirement-plan-first-is-a-gate-jagt-holds)

## Status

Accepted 2026-10-22. Amends [0014](0014-behaviour-is-recorded-as-openspec-specs.md).

## Context

- A task in mode `plan` ran `claude --permission-mode plan`; the session still has to write `plan.md`, `task_visual.md` and report.
- The plan, and the visual where `visualize` is on, must be written and reviewed before any other file changes.

## Decision

- jagt launches no Claude session in the CLI's plan mode; the brief says when to plan first.
- `TaskState.planGate` is `REQUIRED` for mode `plan`, `CLOSED` under autoplan, else `OPEN`.
- While closed, the `PreToolUse` hook (`Bash|Edit|Write|MultiEdit|NotebookEdit`) refuses a write to any file but
  `plan.md`, `task_visual.md` and `task_notes.md` (`PlanFirst`); the refusal counts as `Guard.PLAN_FIRST`.
- `approve-plan` (legal at `PLAN_PENDING`) and the Master's `go` open it through `PlanApproval` and tell the session to start.
- Under autoplan, `outcome=no_plan` with `IN_PROGRESS` opens it; for a `plan`-mode task it is a protocol violation.
- The plan lists every file the session will create after approval.
- Codex declares no `gate` event, so it keeps `--sandbox read-only` for a plan.

## Evidence

- Probe, 2026-10-22: plan mode answered `Cannot write to …/plan.md while in plan mode.` despite an allow rule naming the file.
- It leaves only through an approval dialog in the session's window, which no board verb and no Master can reach.
- A plan-mode session stopped on prompts nobody saw: `update_agent_status`, files outside the worktree, `task_visual.md`.

## Rejected

- Keep the CLI's plan mode and send the plan in the status report: approval stays in the window, and one vendor's switch.
- A headless planner pass, then a fresh session: the code base is read twice and the planning context is lost.
- A heuristic refusal of shell writes: not closed now.

## Reopen when

- Shell writes during planning are seen in practice.
- A CLI's plan mode gains an approval jagt can drive.
