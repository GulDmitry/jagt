# 0212 — The fitness build counts under the same ceilings as the rest

[← decisions](README.md) · on [0176](0176-every-kind-has-a-ceiling.md), [0210](0210-fitness-is-an-included-build-with-its-own-boundary.md),
[0162](0162-a-check-holds-for-all-code.md)

## Status

Accepted 2026-11-20, owner's order of the same day. Amends 0210 ("the Gradle module count stays as it is").

## Context

- `DependencyLinesFitness` skipped any path with a `fitness` segment, with no decision behind it: a check holds for all code (0162).
- The library took jagt's `runMinutes` property, a Spring log level and Spring annotations as its own values.

## Decision

- **The fitness modules count**: `gradle-modules` reads `fitness/settings.gradle` too, `build-logic-files` and
  `dependency-lines-in-build-gradle` the `fitness/` Gradle files; the three numbers rise by what they hold.
- **The library takes every project value as configuration**: the minutes property, the contexts log properties and cache
  pattern are `fitness { ... }` keys with a neutral default; the Spring-annotated declared fitness functions live in jagt's `fitness.yml`.
- **`library-names-no-project` also scans for the framework and the property names**, so such a value cannot return.
- **Its versions come from `junit-bom` and explicit versions**, not a framework BOM.
- **A scan fitness function may leave files out with `except`**: vendored code, ArchUnit's frozen store and recordings carry text no author writes.

## Reopen when

- The fitness build leaves as its own repository: its counts leave with it.
