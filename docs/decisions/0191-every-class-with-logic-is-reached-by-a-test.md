# 0191 — Every class with logic is reached by a test

[← decisions](README.md) · beside [0181](0181-every-module-keeps-80-percent-line-coverage.md)
Rule: [`testing`](../rules/testing.md)

## Status

Accepted 2026-11-17, owner's choice.

## Context

- A module's 0.80 (0181) hides a whole class at 0 %: a 255-line class with branches passed every review untested.
- Measured 2026-11-17: 18 classes with a branch at 0 % in the modules' own reports; 5 once every module's tests are merged.
- A class no test can build through its production constructor is badly designed; writing its test fixes the design.

## Decision

- `testReachCheck` (`jagt.reach`, root) finalizes `test`: it merges every module's `jacoco/test.exec`, reports every module's main classes
  and fails each top-level class with a BRANCH counter above zero and no covered line, naming the class and its module.
- A class reached only by another module's tests counts as reached; nested classes count with their top-level class.
- Nothing is exempt by name: a branchless class (a delegating controller, a config, a mapper) passes by measurement.
- It skips a `--tests` run and a run whose module has no `test.exec`: part of a suite proves nothing about reach.
- Each missing test pins the class's business case, a happy path or a couple of branches; how much it covers is 0181's.
- Ceilings raised by one ([0176](0176-every-kind-has-a-ceiling.md)): `buildLogic` 16 (the `jagt.reach` convention).

## Rejected

- A per-class ratio floor: the owner asked for reach, the module's 0.80 covers the rest.
- Per-module reports only: a core class reached by usecase tests would be named, and surface's own report is empty.
- A list of exempt classes: it grows; measurement exempts the branchless.

## Reopens when

- A class cannot be reached by any test without a production seam only tests call: refactor it, never exempt it.
