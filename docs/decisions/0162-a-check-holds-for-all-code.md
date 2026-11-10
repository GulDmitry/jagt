# 0162 — A check holds for all code

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · amends [0034](0034-null-is-declared-and-the-compiler-checks-it.md),
[0053](0053-a-method-reads-at-cognitive-complexity-twelve-at-most.md),
[0143](0143-checkstyle-gates-parameter-and-component-counts.md), [0146](0146-error-prone-gates-its-default-checks-in-main.md)

## Status

Accepted 2026-11-10.

## Context

- Each linter was adopted on main sources alone; about 6,300 breaches stood in tests and the other source sets.
- Every rule test kept its own module list, so a new module or source set started unchecked.
- A test is code: a test too complex or too duplicated hides a class that is worse.

## Decision

- **A check is a kind** ([`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-kinds)): a linter, a rule test or a convention plugin,
  and it holds for every source set of every project.
- The build applies Checkstyle to every source set and Error Prone with NullAway to every `JavaCompile`, by no name.
- Quality rule tests read `SourceRoots`, which finds every `src/*/java` on disk, and its `ALL` import, tests included.
  `DuplicationTest` still skips the roots its own list names, a list that only shrinks.
- pitest mutates every project's main code, the root's included; test code is not mutated.
- Main-only, by meaning: who in production may act (`FlowDoorsTest`, `TaskOpeningDoorsTest`, `ProductionConstructorTest`,
  `KindsTest`, `UnattendedLoopTest`, `SeamSelectionTest`, `ConfigReferenceTest`, `LogbackLoggersTest`, `BoardAssetsTest`,
  `RingsTest`'s ring rules), and what a test names as its fixture: vendor names, the clock bean, IDE folders.
- The mock ceiling is the collaborator ceiling, 5: a class may hold five collaborators, so its test may mock five.
- A suppression only for a proven false positive no code change avoids, on that one site, its reason on its line.

## Rejected

- A main-only first step: it never widened by itself.
- A scope test or a review question beside the holder: three homes for one fact.

## Reopen when

- A check cannot mean anything outside main and is not about who acts in production.
