# 0129 — CI reports the mutants of a push

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · pipeline: `.github/workflows/ci.yml` · amends
[0127](0127-mutants-run-with-every-test.md)

## Status

Superseded by [0151](0151-mutants-are-judged-locally-only.md) on 2026-11-08.

## Context

- On CI the tree is clean, so 0127's default base `HEAD` mutates nothing: a commit whose writer skipped the
  judgement reached `main` with no survivor shown anywhere.

## Decision

- Both pipelines' unit job runs `test -PpitestDiff=<base>`: a push's `before`, a request's base. `scripts/push-base.sh`
  falls back to `HEAD~1` where the clone does not hold it (a new branch, a force-push). The checkout is full depth.
- A line only a `.git-blame-ignore-revs` commit changed in the range is not judged: a reformat mutates nothing.
- Survivors are printed in the job log and fail nothing, as 0127 holds.

## Rejected

- Failing the job on a survivor: an equivalent mutant has no suppression to clear it (0127).

## Reopen when

- The unit job's `pitestDiff` routinely takes over 10 minutes.
