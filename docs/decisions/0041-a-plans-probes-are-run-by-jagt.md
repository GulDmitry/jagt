# 0041 — A plan's probes are run by jagt

[← decisions](README.md) · rule: [`acceptance-probes`](../../openspec/specs/acceptance-probes/spec.md)

## Status

Accepted 2026-10-14.

## Context

- A ticket's acceptance check was the session's to run and report; the Master stopped a round that skipped it,
  but read the session's word that it had run.
- A gate is deterministic only where it reads facts the agent cannot author (soma, `docs/work-graph.md`).
- `verifyCommand` is the human's, one per project; a task-specific check had no machine-run home.

## Decision

- `plan.md` declares probes in a fenced `probes` block, parsed by one class (`protocol/PlanProbes`): `command`,
  `ref`, `artifact`, at most ten. A line it cannot read refuses the PLAN_PENDING report.
- They are read once, as the plan is put up, into `state.json`: what runs is what was ratified, whatever
  `plan.md` says later.
- At VERIFYING, after the project's command, jagt runs them in the primary worktree, five minutes each, the
  last 20 lines kept. A red one returns the round with its result; no model is asked.
- A command runs as argv with no shell, judged first by `ToolGate` and run under the worktree's git hooks: the
  fence a session's own line meets. `ToolGate` moves to `service/` so a job can ask it.
- The Master reads the probes when judging a plan, and their results when judging a round.

## Rejected

- Reading probes from `plan.md` at VERIFYING: the session can edit it after the go.
- A shell for `command`: a pipeline is a script the plan names, and argv leaves no grammar to guard.
- A port for the guard implemented in `surface/`: a class two kinds use belongs to `service/`.
- Probes per repository: the plan lives in the primary worktree; no task has asked for more.

## Reopen when

- A probe needs a second worktree, or a check the three types cannot express.
- A probe's output exhausts memory before its five minutes: `Processes` buffers whole output.
