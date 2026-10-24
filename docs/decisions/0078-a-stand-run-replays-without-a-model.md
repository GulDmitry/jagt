# 0078 — A stand run replays without a model

[← decisions](README.md) · rule: [`testing`](../rules/testing.md#the-e2e-matrix-and-the-model-evals)
· follows: [0073](0073-cases-are-the-humans-and-the-stand-comes-first.md)

## Status

Accepted 2026-10-24.

## Context

A live stand run costs minutes and tokens, so a change to jagt's frame was rarely checked against real model
behaviour again.

## Decision

- A live run writes `recording.jsonl`: each model move typed and keyed to the turn it answered, read from what jagt
  already writes. The backend log gives turns, session calls (`mcp tool called`) and read answers (`assistant read
  answered`), both DEBUG; worktree snapshots give verdicts and the session's own files; origin.git gives its code.
- `orchestrator.agent.cli: replay` plays one back behind the existing ports: `ReplayAgentRuntime` answers each turn
  through `/mcp` as a session would, `Replay{RoundReviewer,TrackerAssistant,CodeHostAssistant}` the reads.
- A turn opening that the recording did not answer, or a recorded turn never opening, ends the replay as a
  divergence naming the step: the signal that the frame changed behaviour.
- A read's answers repeat their last: how often jagt polls is its clock's, not a model's.

## Rejected

- Recording code inside jagt's flow: two log fields were the only facts missing.
- Matching turns by id: a replay compares steps, which is what the frame decides.

## Reopen when

- A recorded run needs a per-round tree its branch cannot give, or a Master answer read, both unrecorded today.
