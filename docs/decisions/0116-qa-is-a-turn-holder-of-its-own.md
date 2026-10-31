# 0116 — QA is a turn holder of its own

[← decisions](README.md) · rule: [`task-flow`](../../openspec/specs/task-flow/spec.md)

## Status

Accepted 2026-10-31, signed off by the owner 2026-10-30; built in the commits of `qa-role`, its spec delta archived with them.
A dispute rides a question on the DEPLOYED card: the Master in `act` answers a first, the human a second, and the arbiter's
answer reaches the session, not QA. The `done` signal, a restart re-run of an open turn, scripted flows and the browser are not built yet.
Builds on [0072](0072-the-conversation-turn-is-the-machines.md),
[0073](0073-cases-are-the-humans-and-the-stand-comes-first.md),
[0010](0010-the-humans-own-word-stands-over-the-master.md), [0017](0017-the-master-is-seen-in-one-feed-window.md).

## Context

- The session checks its own deploy (`DeployCheckJob`), so it grades its own work.
- The Master judges the code, "fit to test"; nobody judges the deployed change against the ticket.

## Decision

- QA is a component equal to the Master and independent of it: `Turn.Holder.QA`, `CaseBook.By.QA`, its own brief, cases and `orchestrator.qa.*` keys.
- QA is a label (`qa passed`), never a status: nothing sits between `DEPLOYED` and `done`.
- Gate: deployed, tracker item open, each target's pipeline green, environment in `orchestrator.qa.environments`; landing is read, never guessed.
- Trigger: the engine opens one check per deploy on the tip's sha; a verdict on a sha off the deploy branch is dropped.
- Loop: QA → session as a round; a decline QA disputes goes once to the Master in `act`, else to the human; the human's word ends it.
- The Master never reviews a QA check; a not-met after a Master deploy is no `MasterBrake` strike, but a `lesson:` line in `MasterRecord`.
- One port, two strategies behind it, chosen by `orchestrator.qa.enabled`: on, QA checks and `DeployCheckJob` asks the session nothing; off, as today.
- Before ship the session checks its own work; no QA model runs before merge.
- At `accept` QA first writes one row per acceptance criterion in `qa-report.md`, then fills met / not met / unprovable: one list, one home.
- QA runs sonnet by default (`orchestrator.qa.model`); its lines join the Master's feed, each naming its role.
- Choices: a derived label shown while the sha is the tip; landing proven by host job, else a named probe, else a question;
  a dispute is a `QUESTION` on the DEPLOYED card, a second a `PERMISSION`;
  a decline holds `DEPLOYED` as `no_changes`; a not-met goes back in any mode;
  a declined ending has no mark, its why in the hover; no human rerun verb; no second dispute after the human ruled.

## Rejected

- A status between `DEPLOYED` and `done`: a label carries it, the machine gains no state.
- The Master as QA's gate: one opus read per check; if `qaEval` shows wrong findings frequent, QA moves to opus.
- Both strategies on one deploy: two checks, two verdicts, one task.
- QA before merge: it races the session, the Master and the reviewer on one branch.
- A prose-routed dispute (`question` by context): routing by prose is what 0072 removed.
- A QA note at ship: the ship step keeps the request description empty; the checklist is QA's own first write.
- Ticket readiness at plan as a feature: the Master's "unsure what the ticket asks" already turns it into a question; at most a qa-brief line.

## Reopen when

- `qaEval` or a live run shows QA's feed lines outnumber the Master's several times over.
- `qaEval` shows wrong QA findings frequent enough that the round costs more than a Master read.
- `qaEval` shows unprovable rows caused by untestable criteria: ticket readiness at plan returns.
