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
- `jacocoTestCoverageVerification` (LINE, COVEREDRATIO, 0.80 per module) finalizes `test`; a miss fails naming the module and its ratio.
- A `--tests` run skips the verification: part of a suite proves nothing about a ratio.
- The root's floor is 0.60, its measured ratio: the launch loop in `OrchestratorApplication` has no test yet. Raise it to 0.80 with that test.

## Rejected

- One ratio over all modules: a well-tested module would hide a bare one.
- Branch coverage: the owner asked for lines.

## Reopens when

- The root reaches 0.80: its floor goes.
