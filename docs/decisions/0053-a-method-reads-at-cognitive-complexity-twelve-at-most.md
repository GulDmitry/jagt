# 0053 — A method reads at cognitive complexity 12 at most

[← decisions](README.md) · rule: [`docs/rules/style.md`](../rules/style.md#code-quality)

## Status

Accepted 2026-10-21.

## Context

- No check measured how hard a method is to read; fifteen methods stood above 15, the worst at 35.
- Error Prone, Checkstyle, SpotBugs and ArchUnit have no cognitive complexity; Sonar does, behind a server.

## Decision

- `CognitiveComplexityTest` runs PMD's `CognitiveComplexity` over every ring's `main` inside `./gradlew test`.
- A method above 12 fails the gate; every method is checked, not only a diff, in about a second.
- The ceiling only falls: `refresh` lowers it in steps (15 → 12 → 10) once a handful stand above the next.

## Rejected

- Sonar's `java:S3776`: the metric's origin, but a server cannot gate an offline `./gradlew test`.
- Cyclomatic or NPath complexity: they count paths, not the nesting a reader carries.
- A diff-only check: the debt in an untouched method would stand, and a worktree has no one base to diff.
- The Gradle `pmd` plugin: a second gate beside `test`.
- A baseline of tolerated methods: the fifteen were split instead.

## Reopen when

- PMD's count drifts from Sonar's on a construct jagt uses.
- A method cannot fall under the ceiling without a split that reads worse.
