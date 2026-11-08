# 0152 — A stop lifts on the next model tier

[← decisions](README.md) · rule: [`task-stops`](../../openspec/specs/task-stops/spec.md)

## Status

Superseded in part by [0153](0153-mine-names-what-the-master-leaves.md): the Master's door no longer refuses `escalate`.

Accepted 2026-11-08 by the owner's order. Beside
[0140](0140-in-act-the-master-decides-a-stop-for-a-suspected-loop.md): a fourth answer to a stop.

## Decision

- `escalate` is a legal move in `FlowRules`, offered only on a STALLED, SHIPS, ROUNDS, UNCHANGED_FAILURE or QA_FAILED stop.
- A model must stand above the one the session runs on; the trigger is a stop `TaskStops` set, never a model's judgement.
- `agent.tiers` lists the models lowest first; unset, the runtime offers its own (`AgentRuntime.tiers`), the vendor in its adapter.
- Names the agent does not accept are dropped; the first entry stands for the session's start model.
- Every session start records its ladder on the task (`ModelTier`): the start model, then the tiers listed after it.
- The move lifts the stop as `lift` does, raises the rung and starts the session again on that model, the work carried over.
- `escalate` is human-only: the Master's door refuses it, and nothing escalates unasked.
- It is never sticky: a start records the first rung again, and a plain lift lowers the rung.
- A stop a read ends, PACE, MISS and PAUSE offer no tier: a heavier model does not answer them.
- The card gains no mark: the verb's button is the fact, and the title hover names the model while it runs a tier up.

## Rejected

- A tier per role: every session is the one role that failed; a table nobody asked for.
- Escalating by a model's reading of the failure: 0061's guard holds when the model is the loop.
- The ladder read from `jagt.yml` per card: the projection would need the config; the record carries what the offer needs.
- The Master pressing it in `act` now: its answer is the brief's, which a new line would change and `roleEval` must prove.

## Reopen when

- A store shows a lifted stop passing on a heavier model: the Master's answer may then name the tier.
- Escalated tasks stop again on the same trigger more often than a plain lift's do: the ladder is no use.
