# 0181 — Every module keeps 80% line coverage, gating `test`

[← decisions](README.md) · beside [0125](0125-mutants-are-judged-on-the-diff-at-each-commit.md)
Rule: [`testing`](../rules/testing.md)

## Status

Accepted 2026-11-16, owner's choice.

## Context

- Mutants on the diff judge changed lines only; nothing stopped a module's untested code from growing.
- Measured 2026-11-16 (line ratio, each module's own tests): root 0.60, statechart 0.99, core 0.91, usecase 0.84, adapter 0.93, surface 0.93.

## Decision

- JaCoCo, applied to every project in `jagt.conventions.gradle`; JaCoCo's defaults otherwise.
- `jacocoTestCoverageVerification` (LINE, COVEREDRATIO, row `line-coverage`: 0.80) finalizes `test`; a module's miss fails naming it and its ratio.
- A `--tests` run skips the verification: part of a suite proves nothing about a ratio.

## Rejected

- One ratio over all modules: a well-tested module would hide a bare one.
- Branch coverage: the owner asked for lines.

## Reopens when

- A module cannot reach 0.80 without a test that only a production seam would allow: its exclusion is named here with its reason.
