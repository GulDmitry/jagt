# 0151 — Mutants are judged locally only

[← decisions](README.md) · rule: [`testing`](../rules/testing.md)

## Status

Accepted 2026-11-08 by the owner's order; supersedes [0129](0129-ci-reports-the-mutants-of-a-push.md).

## Context

- CI's survivors failed nothing and nobody read them; a push range holding a reformat ran 54 min.

## Decision

- CI runs `test` alone; its clean tree gives `pitestDiff` no changed line.
- The writer judges survivors before each commit, as 0127 holds.
- A diff mutates once (JAGT-244, 2026-11-24): `pitestDiffSurvivors` declares the changed-lines file, the target classes and the reports as inputs, so Gradle's up-to-date check serves the stored survivors.

## Reopen when

- A commit reaches `main` with a survivor its writer never judged, and a reader of CI's list exists.
