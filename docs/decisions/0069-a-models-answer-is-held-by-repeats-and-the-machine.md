# 0069 — A model's answer is held by repeats and the machine

[← decisions](README.md) · rule: [`judgement-resilience`](../../openspec/specs/judgement-resilience/spec.md),
[`testing`](../rules/testing.md)

## Status

Accepted 2026-10-23.

## Context

An eval ran each case once, so a flake read as a regression. Judging one prompt change took four runs on the change
and four on the base by hand (3de263cf). A verdict also passed on the model's words alone, with nothing said of
what jagt heard.

## Decision

- A case passes on a majority of its runs: 3 runs, 2 passes, both set by `-PevalRuns` / `-PevalPasses`.
- `EvalQuorum` stops once the outcome is decided and reports every failed run's own message.
- It lives in its own source set: a test class in `src/test` would leak into the evals' Spring scan.
- `masterEval` asserts what the read gate heard: a call naming the run's worktree, and no refusal.
- A pattern line is one yes-or-no fact, held by `PatternLinesTest`; a judge cannot be steady on a taste word.

## Rejected

- A quorum of different models: cost, and whatever a light model can do a light model does.
- Embedding similarity of answers: it does not check meaning.

## Reopen when

- A case needs more than five runs to settle, or a run costs less than a repeat saves.
