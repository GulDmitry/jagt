# 0193 — The stand harness is a module outside the assembly

[← decisions](README.md) · beside [0162](0162-a-check-holds-for-all-code.md), [0163](0163-a-test-shares-a-builder-a-fake-or-a-scanner.md),
[0174](0174-the-stand-runs-in-jagts-container.md)

## Status

Accepted 2026-11-17, owner's choice.

## Context

- The stand's 61 frozen harness entries (JAGT-86) sat in the `stand` source set of the root project, which the checks of 0162
  and 0163 do not reach: its classes are neither `*Test` nor main.
- Its unit tests had no home that kept the harness out of the shipped jar.

## Decision

- `:stand` is a Gradle module beside `:core`, `:usecase`, `:adapter`, `:surface`: `src/main` the harness, `src/test` its unit tests,
  `src/stand` the scenarios and compose files.
- It is not in the assembly, so no shipped class depends on it and the jar carries none of it.
- Its checks are the other modules': `:stand:test` runs with `test`, Checkstyle, Spotless, JaCoCo and the reach check cover it.
- `standTest` stays out of `check`: it spends tokens and needs docker.
- The stand suite's path in `testing-suites.md` is `^orchestrator-backend/stand/`.

## Rejected

- Harness classes nested under a `*Test` class: 0163 forbids shared helpers in tests, and a harness is not one test's.
- A carve-out in `areTopLevelInTestSet` for the stand set: 0162 says a check holds for all code, an exception is a hole.
- A module-name constant listing `stand` beside the others: 0162's module lists would name it, one more place to forget.
- A `testFixtures` set of `:core`: the harness drives the whole assembly, and a fixtures set ships no scenarios.

## Reopens when

- A harness member is called only by its own unit tests: it is dead code or a seam only tests use, remove or merge it.
- A second harness module is wanted: the module then needs a kind in `ARCHITECTURE.md`, not a copy.
