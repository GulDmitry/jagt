# 0127 — Mutants run with every test

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · plugin: `build-logic/src/main/groovy/jagt.pitest.gradle`
· supersedes in part [0125](0125-mutants-are-judged-on-the-diff-at-each-commit.md)

## Status

Accepted 2026-11-02 by the owner's order.

## Context

- 0125 asked the writer to run `pitestDiff` before each commit changing `main`. On 2026-11-02 a fix and a briefed
  agent's commit both went in without it: a step a session must remember is a step it skips.

## Decision

- Supersedes 0125's bullet "A commit changing `main` runs `./gradlew pitestDiff -PpitestDiff`".
- Every `test` task is finalized by `pitestDiff`: pitest on the `main` classes changed against `HEAD`, the tree
  included, listing the survivors on a changed line. `-PpitestDiff=<base>` names another base.
- No changed `main` line: it prints so and mutates nothing. A committed tree has none.
- A changed `main` file with no mutant on its changed lines is named, so it cannot read as all killed.
- A survivor fails nothing: its writer judges it before the commit (0125), as an equivalent mutant has no
  suppression to clear it.

## Rejected

- A git hook: AGENTS.md forbids one.
- Failing the build on a survivor: an equivalent mutant would block every commit touching its line.

## Reopen when

- A test run's `pitestDiff` routinely takes over 5 minutes.
