# 0074 — A discovery warning fails the run

[← decisions](README.md)

## Status

Accepted 2026-10-24, by the owner.

## Context

JUnit reports a test it cannot discover (a private or static `@Test`, a static `@Nested`, a malformed
`@ParameterizedTest` source) as a discovery issue. Only issues at or above the critical severity fail the run; the
rest are logged and the test never runs. The default is `ERROR`, "may change in a future release".

Measured 2026-10-24 with `INFO`, the lowest level: `test`, `e2eTest` and `boardTest` held no issue.
A canary class with a private `@Test` passed green at `ERROR` and failed the run at `WARNING` and at `INFO`:
`ERROR` hides the case the check exists for.

## Decision

- Every `Test` task sets `junit.platform.discovery.issue.severity.critical` to `WARNING`, once, in the
  `tasks.withType(Test).configureEach` of `orchestrator-backend/build.gradle`: the suites and evals inherit it.
  Any warning or error fails the run with `DiscoveryIssueException`.
- `WARNING` and not `INFO`: an info or notice is a remark, not a lost test.
- Not an option in `jagt.yml`: it is the build's, not the user's.

## Rejected

- Retry or quarantine of flaky tests: a red run is the code's to fix.
- `@Suite` setup: the suites run in parallel, no shared `@BeforeAll`.
- Precondition extensions: a shared builder, which [testing](../rules/testing.md) forbids.
- Open Test Reporting: nothing reads it.
- Leaving the default `ERROR`: a private `@Test` runs nowhere and reports nothing.

## Reopen when

- A JUnit release reports a warning for something that loses no test, and the build fails on it.
