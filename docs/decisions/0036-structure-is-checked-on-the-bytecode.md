# 0036 — Structure is checked on the bytecode

[← decisions](README.md) · rule: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-rings)

## Status

Accepted 2026-10-12.

## Context

- `RingsTest` and `KindsTest` matched package and class names in the source text.
- Text misses a dependency reached through a return type, and cannot see a cycle between two folders.
- `withStatus` callers and the five-collaborator ceiling were rules no test held.

## Decision

- ArchUnit reads the compiled classes: ring dependencies and cycles (`RingsTest`), where a class lives
  (`KindsTest`), the two status doors (`FlowDoorsTest`), five constructor parameters per component
  (`CollaboratorCeilingTest`).
- Text stays where bytecode is blind: Lombok (source retention), OS and vendor names, a type named in a comment.
- The Gradle modules stay the first line: an outward import does not compile.

## Rejected

- `archunit-junit5` with `@ArchTest` fields: the suite's tests are plain JUnit, parameterized where they repeat.
- A frozen baseline (`FreezingArchRule`): the code had no violation to freeze.

## Reopen when

- A JDK jagt moves to is one ArchUnit cannot read yet.
- `service` and `service/master` stop depending on each other: their cycle joins `noTwoRingsDependOnEachOther`.
