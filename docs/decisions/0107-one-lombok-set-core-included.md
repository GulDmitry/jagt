# 0107 — One Lombok set for the whole tree, core included

[← decisions](README.md) · rule: [`style.md`](../rules/style.md)

Supersedes the Lombok clause of [0007](0007-only-adapter-names-a-vendor-or-an-os.md).

## Status

Accepted 2026-10-29.

## Context

- `core/` carried no Lombok so its records and rules ran without a container (0007); the rest of the tree
  already used `@RequiredArgsConstructor`, `@Slf4j` and `@With`.
- The centre therefore hand-wrote assign-only constructors, positional withers and builders the other rings did not.
- The first line of defence, "no Lombok in core", was a text test; the set a ring may use was written nowhere.

## Decision

- One set everywhere, `core/` included: `@RequiredArgsConstructor`, `@Slf4j` (`core/` does not log),
  `@With` on records, `@Builder(toBuilder = true)` on records with defaults (defaults in a hand-declared
  `<Name>Builder` stub), `@Getter` only on a stateful class a record cannot be.
- Every other annotation is `flagUsage = error` in `orchestrator-backend/lombok.config`, so the build enforces the
  set and no text does.
- A hand-written constructor, wither or builder stays where it validates, stamps or computes.
- `lombok.addLombokGeneratedAnnotation = true` and Spring's `Qualifier`, `Value`, `Lazy` are copyable annotations.

## Rejected

- `@Data`, `@Value`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`: records or immutable state cover them.
- `@EqualsAndHashCode`, `@ToString`: a record's own are the contract.
- `@NonNull`: NullAway decides nulls ([0034](0034-null-is-declared-and-the-compiler-checks-it.md)).
- `@SneakyThrows`, `@Cleanup`, `@Synchronized`, `@Locked`: hide checked exceptions, resources or locks.
- `@UtilityClass`, `@Delegate`, `@FieldDefaults`, `@Accessors`, `@Helper`, `@ExtensionMethod`: change the
  language or the shape of a class beyond boilerplate.
- `@Singular`, `@Jacksonized`, `@SuperBuilder`, `@WithBy`: a second way to build or bind what a record's builder does.
- `val`, `var`: the type is stated.
- `@Log`, `@CustomLog` and every logger but `@Slf4j`: one logging facade.
- `@StandardException`: its generated constructors are never called from main, and `ProductionConstructorTest` rejects them.
- `@Tolerate`: Lombok has no `flagUsage` key for it, so it is the one rejection no build enforces.

## Reopen when

- A Lombok upgrade makes generated code something NullAway or the bytecode tests cannot read.
- A rejected annotation removes more code than it hides, shown on a real class.
