# 0163 — A test shares a builder, a fake or a scanner

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · amends [0162](0162-a-check-holds-for-all-code.md)

## Status

Accepted 2026-11-11. Amended by [0164](0164-a-unit-test-is-named-after-its-main-class.md): a unit test is named after its main class.

## Context

- 0162 put the duplication gate on tests; the first deduplication pass moved repeated setup into shared holder
  classes that built data, wired services, read results and asserted, all mixed.
- A shared step that acts or asserts hides the test's story; repeated object setup is the one repetition a test
  cannot state inline without copying it.

## Decision

- Shared test code is one of three: a test builder, a fake of a production seam, a rule test's file scanner.
  No helper method and no other shared class.
- A test builder is `Test<Thing>Builder` in the test root: a public record with Lombok `@With`, a static `<thing>()`
  setting fixed valid defaults, one `build()` calling a production constructor or builder.
- A builder holds no logic: no branch, loop, mock, `verify`, assertion, clock or random value.
- A builder of outside state (a git repository on disk) may write in `build()`.
- A record whose production `defaults()` and `@With` serve production needs no builder; a `defaults()` only
  tests call moves into one.
- Main holds no constructor or member that only tests call; `ProductionConstructorTest` widens to members.
- A service too long to wire is not built by a builder: its collaborator count is fixed in main.

## Rejected

- Instancio: random filling builds states the flow never reaches; fixed-value builders need no library.
- Builder classes with `@AllArgsConstructor`: 0107 rejects it, and a record carries `@With` alone.
- Shared holders that wire services or read results: the first pass's shape.
- Inlining `ShippedRoot.configure`: it copies one Spring root's properties into every context test; it stays.
- Builders in place of `GitRepos`: its scripted git scenarios are a fake of a repository; it stays.
- Inlining `BoardPageContext`'s page actions: they drive the board's page for every board test; they stay.
- Inlining `FlowHarness`'s waits, driver actions and world setup: they drive jagt for every e2e test; they stay.
- Moving the model evals' helpers into new harness classes: it moves their freeze entries, removes none; they stay frozen (0162).
- Nesting a test's Spring event and listener: a fake of `ApplicationEvent` or `ApplicationListener` fakes the seam Spring dispatches through.
- Folding a repeated call under 75 tokens back into a helper: `DuplicationTest` is the judge.

## Reopen when

- A builder needs more record components than Checkstyle's ceiling allows.
- A duplication that no builder, fake or parameterized test removes.
