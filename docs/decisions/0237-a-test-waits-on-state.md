# 0237 — A test waits on the state it asserts, never on a timeout, a sleep or a thread of its own

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · on [0162](0162-a-check-holds-for-all-code.md),
[0163](0163-a-test-shares-a-builder-a-fake-or-a-scanner.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-24, by the owner's order (JAGT-246).

## Context

- A test with a timeout, a sleep or a thread of its own is fast on one machine and flaky or slow on another.
- A wait on the state a test asserts (an awaited condition, a returned value) is as fast as the code.
- Some tests have a thread or a timeout as the thing under test: a runner's timeout, a lock held by another process,
  two callers at once.

## Decision

- Every test source set (`test`, `boardTest`, `e2e`, `stand`, the fitness build's tests) uses none of `@Timeout`,
  `assertTimeout*`, `Thread.sleep`, `new Thread(`, `ExecutorService`, `Executors.` or `CompletableFuture`
  (`CompletableFuture.completedFuture` is a value, not a thread).
- A test waits on the state it asserts; its hang guard is the suite's ceiling ([0139](0139-every-suite-ends-within-one-ceiling.md)).
- The allow form is a line ending in `// allowed: 0237`: the thread or timeout is what the test or its fake exists to simulate.
- A second reason is a new decision record, and the line names that one.
- `timing-in-tests` in `fitness.yml` counts the other lines, `max 0`.

## Rejected

- A shorter timeout: it is still a clock the machine decides.
- A shared wait helper: a test shares a builder, a fake or a scanner only (0163).

## Reopen when

- A test needs a wait no state can express, in more than one place, from one cause.
