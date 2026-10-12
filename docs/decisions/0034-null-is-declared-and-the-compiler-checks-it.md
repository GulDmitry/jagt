# 0034 — Null is declared, and the compiler checks it

[← decisions](README.md) · rule: [`style.md`](../rules/style.md#code-quality)

## Status

Accepted 2026-10-12.

## Context

- Records and services pass null as a meaning ("not read yet", "the project's default"), named only in a comment.
- An unguarded dereference of such a value surfaced as an NPE at run time, if ever.
- Spring Framework 7 and Boot 4 ship JSpecify-annotated, so their nullness is already declared.

## Decision

- JSpecify `@Nullable` marks every field, component, parameter and return that may hold null; unmarked means non-null.
- NullAway in JSpecify mode, run through Error Prone on main sources, fails the build on a violation.
- It covers `dev.jagt` by package prefix, so a package is checked even before it is marked.
- Every package is `@NullMarked` in its `package-info` too, so the IDE flags null as typed; `NullMarkedTest` holds it.
- Error Prone's own checks stay off: NullAway is the one adopted.
- Tests are not checked: they pass null on purpose.

## Rejected

- `@NullMarked` alone, NullAway's `OnlyNullMarked`: a package missing it would go unchecked.
- One `@NullMarked module`: needs JPMS, and a package annotation does not reach its subpackages.
- Annotations without a checker: a soft contract nothing enforces.
- `Optional` for every nullable field: Jackson-bound records and the state file would change shape.

## Reopen when

- Error Prone or NullAway lags a JDK jagt moves to.
- NullAway's compile cost is felt in the gate.
