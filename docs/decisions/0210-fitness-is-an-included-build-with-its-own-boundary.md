# 0210 — `fitness/` is an included build with its own boundary

[← decisions](README.md) ·
on [0209](0209-one-fitness-yml-declares-every-fitness-function-in-a-reserved-tree.md), [0162](0162-a-check-holds-for-all-code.md)

## Status

Accepted 2026-11-20, owner's order of 2026-11-18.

## Context

- The fitness feature is meant to leave as its own library and Gradle plugin.
- A library naming jagt's package, rings or paths cannot leave.

## Decision

- `orchestrator-backend/fitness/` is an included build, beside `build-logic/`; the Gradle module count stays as it is.
- No file under it names `dev.jagt`, a ring, a path or a vendor: jagt's selectors live in jagt's `fitness.yml`, its domain fitness functions in jagt.
- It keeps its own `decisions/` and `AGENTS.md` from its first commit.
- It checks its own names with its own `fitness.yml` (`LibraryChecksItselfTest`).
- Its `defaults.yml` over itself is not yet green, a defect under [0162](0162-a-check-holds-for-all-code.md): jagt's rule tests cannot see it.
- Extraction is the folder published as a dependency and a plugin; jagt swaps `includeBuild` for a version, library code unchanged.

## Rejected

- A module of the main build: it sees jagt's sources, so a `dev.jagt` selector leaks in unseen.
- A copy of the fitness functions in a second repository now: two homes for one number.

## Reopen when

- The folder leaves as its own repository.
