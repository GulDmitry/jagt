# 0251 — The classes the fixes return to are named at every release

[← decisions](README.md) · rule: [`testing-suites`](../rules/testing-suites.md#hot-spots) ·
kind: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-kinds) ·
on [0176](0176-every-kind-has-a-ceiling.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-25.

## Context

- Size and complexity rows bound a class's shape, not how often fixes return to it; a class fixed ten times a month is
  the next cut whatever its size.
- Every fix commit carries a `Defect-family:` trailer ([`testing`](../rules/testing.md)); `L` is the tests themselves.
- Hot-spot analysis, change frequency against complexity, is the published practice for finding the next cut
  (Adam Tornhill, *Your Code as a Crime Scene*, Pragmatic Bookshelf, 2015).

## Decision

- **Task `hotSpots`** (`build-logic`, `dev.jagt.gradle.hotspots`) counts, per main-source class, the non-merge commits of the last
  30 days whose trailer names a family other than `L`, and prints the top ten as `<class> <fix-commits> <lines>`.
- **`hotSpots.max`** in `gradle.properties` is the top count: `hotSpots` fails above it naming the class, and below it asking to
  lower it, so it only falls. Measured 2026-11-25: `TaskState` 10 (866 lines).
- **A shallow checkout fails** naming the missing history; the window reads an injected `Clock`.
- **The release review runs it**: a class over the number gets its cut as its own task, or a decision naming why it stays.
- A renamed class counts from its new path; a class deleted counts nowhere.

## Rejected

- A per-push CI run: the number moves by history, so one release-time read decides as well.
- A count over all of history: old fixes of a since-cut class would hide today's hot spot.
- A row of `fitness.yml`: the read is git history, no source scan or class graph.

## Reopen when

- Fixes stop carrying the trailer, or a rename-following count shows the path count misses a hot class.
