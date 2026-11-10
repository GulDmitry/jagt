# 0160 — The chart is an instance and start work is an event

[← decisions](README.md) · rule: [`style`](../rules/style.md) · beside
[0107](0107-one-lombok-set-core-included.md)

## Status

Accepted 2026-11-10 by the owner.

## Context

- Static mutable state and `@PostConstruct` work reach around the constructor `ProductionConstructorTest` guards.
- Measured 2026-11-10: `TaskChart` holds 3 static mutable fields and `ToolGate` 1; 7 `@PostConstruct` hooks follow events or install state.
- About 71 static holders (~400 call sites) are pure and mutation-covered.

## Decision

- **A static field is final and immutable**; a static method reads its arguments and constants alone.
- **Pure static holders stay**; file holders stay (`@TempDir` is the seam).
- **`TaskChart`, `TaskReducer`, `Checkers` and `FlowRules` are instances** built by `FlowWiring`, taken through the constructor;
  a role composition is the context's, never the process's.
- **Start work becomes `StateWritten` events**, listened to, not `@PostConstruct` (follow-up task).
- **`StaticStateTest` and `LifecycleHooksTest` freeze the offenders** in `src/test/resources/archunit/`; the store only shrinks.
- Exempt by name: `TaskAliasLogFilter`, `AbortedConnectionFilter` (Logback predates the context).

## Rejected

- Banning static holders: 71 pure holders, mutation-covered, would gain a constructor and nothing else.
- Failing the build at once: offenders would block unrelated work; the frozen store ratchets.

## Reopen when

- The frozen store holds no entry: the rules then drop `FreezingArchRule`.

Sources checked 2026-11-10: Spring Framework 7 DI and bean lifecycle reference; Spring Boot 4.1 application events;
ArchUnit 1.5.x `FreezingArchRule`; Effective Java items 1, 3, 4, 5.
