# 0210 — `fitness/` is an included build with its own boundary

[← decisions](README.md) ·
on [0209](0209-one-fitness-yml-declares-every-fitness-function-in-a-reserved-tree.md), [0162](0162-a-check-holds-for-all-code.md)

## Status

Accepted 2026-11-20, owner's order of 2026-11-18.
Amended by [0212](0212-the-fitness-build-counts-under-the-same-ceilings-as-the-rest.md): its modules count under the ceilings.

## Context

- The fitness feature is meant to leave as its own library and Gradle plugin.
- A library naming jagt's package, rings or paths cannot leave.

## Decision

- `orchestrator-backend/fitness/` is an included build, beside `build-logic/`;
  its modules count under the ceilings of [0212](0212-the-fitness-build-counts-under-the-same-ceilings-as-the-rest.md).
- No file under it names `dev.jagt`, a ring, a path or a vendor: jagt's selectors live in jagt's `fitness.yml`, its coded fitness functions in jagt.
- It keeps its own `decisions/` and `AGENTS.md` from its first commit.
- It checks its own names with its own `fitness.yml` (`LibraryChecksItselfTest`).
- Its `defaults.yml` holds over itself: `LibraryChecksItselfTest` runs every `defaults.yml` function an engine measures over `fitness/` and passes;
  a red is a defect under [0162](0162-a-check-holds-for-all-code.md).
- Extraction is the folder published as a dependency and a plugin; jagt swaps `includeBuild` for a version, library code unchanged.

## Rejected

- A module of the main build: it sees jagt's sources, so a `dev.jagt` selector leaks in unseen.
- A copy of the fitness functions in a second repository now: two homes for one number.

## Reopen when

- The folder leaves as its own repository.
