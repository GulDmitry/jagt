# 0129 — CI reports the mutants of a push

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · pipeline: `.github/workflows/ci.yml` · amends
[0127](0127-mutants-run-with-every-test.md)

## Status

Accepted 2026-11-02 by the owner's order.

## Context

- On CI the tree is clean, so 0127's default base `HEAD` mutates nothing: a commit whose writer skipped the
  judgement reached `main` with no survivor shown anywhere.

## Decision

- Both pipelines' unit job runs `test -PpitestDiff=<base>`: a push's `before`, a request's base. `scripts/push-base.sh`
  falls back to `HEAD~1` where the clone does not hold it (a new branch, a force-push). The checkout is full depth.
- Survivors are printed in the job log and fail nothing, as 0127 holds.

## Rejected

- Failing the job on a survivor: an equivalent mutant has no suppression to clear it (0127).

## Reopen when

- The unit job's `pitestDiff` routinely takes over 10 minutes.
