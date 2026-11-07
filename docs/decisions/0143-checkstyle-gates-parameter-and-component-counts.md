# 0143 — Checkstyle gates parameter and component counts

[← decisions](README.md) · rule: [`style`](../rules/style.md)

## Status

Accepted 2026-11-07.

## Context

- Records had grown positionally to dozens of components; nothing failed the build on a long parameter list.
- `CollaboratorCeilingTest` counts a bean's collaborators, not a record's components or a method's parameters.

## Decision

- Gradle's `checkstyle` plugin on every module's main sources runs `ParameterNumber` and `RecordComponentNumber`
  at Checkstyle's own defaults (7 and 8); `test` depends on `checkstyleMain`, so the gate fails on a breach.
- `gradle/checkstyle/suppressions.xml` lists today's breaches by file and check, each with its reason and estimated cost;
  zero is the target, and a split by composition deletes its line.
- `UnusedImports` and `RedundantImport` gate main the same way, at their defaults.
- A bean keeps `CollaboratorCeilingTest`'s 3/5.
- The same config is Checkstyle's bundled Google checks with every Javadoc check off (comments default to none);
  google-java-format 1.37.0 laid the layout once, and Checkstyle stays the one gate: no Spotless.

## Rejected

- A jagt-written ArchUnit count: the industry-standard library first.
- Limits of our own: the defaults are what a reader expects.

## Reopen when

- The suppression list is empty and a lower limit is asked for.
