# 0249 — A component exposes no static helper

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md) "Code" · kind: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-kinds) ·
on [0176](0176-every-kind-has-a-ceiling.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-25.

## Context

- `GitDeploy` carried two public statics, `shortSha` and `deployBranch`, called from five classes that read no deploy state.
- A static on a bean is a function with the bean's name on it: callers import the whole service for it, and a test mocking
  the service cannot stub it.
- `GitDeploy` also held the revert and five nested exceptions, 936 lines in all.

## Decision

- **Row `static-helpers-on-components`** (archunit, `measure: static-helpers`, `set: all`) counts each public static method of
  a `@Component`-meta-annotated class; the number only falls.
- **The count at the start was 33; this commit lowers it to 31** by moving `shortSha` to `Shas` and `deployBranch` to
  `TaskBranches`, plain holders that are no component.
- **`GitRevert` owns the revert**, `WorktreeConflicts` the unmerged paths and resolution progress both classes read;
  each exception is a class of its own.
- The 31 left are entry points, factories and pure readers named in the failure list; each falls when its owner is next cut.

## Rejected

- Max 0 now: 31 uses need their own reading, not a blind move.
- A rule on all public statics: records and holders legitimately carry factories.

## Reopen when

- A component needs a static for a framework reason the row cannot tell from a helper.
