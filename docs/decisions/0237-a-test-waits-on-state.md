# 0237 — A test has no timeout, no sleep and no thread of its own, with no exemption

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · on [0162](0162-a-check-holds-for-all-code.md),
[0163](0163-a-test-shares-a-builder-a-fake-or-a-scanner.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-24, by the owner's order (JAGT-246).

## Context

- A test with a timeout, a sleep or a thread of its own is fast on one machine and flaky or slow on another.
- A wait on the state a test asserts (an awaited condition, a returned value) is as fast as the code.
- A test whose subject is a thread, a lock or a timeout earns no keep: stability outranks that coverage.

## Decision

- Every test source set (`test`, `testFixtures`, `boardTest`, `e2e`, `stand`, `installTest`, `linuxTest`, the evals, the fitness build's tests)
  uses none of `@Timeout`, `assertTimeout*`, `Thread.sleep`, `new Thread(`, `Thread.ofPlatform`, `Thread.ofVirtual`,
  `startVirtualThread`, `ExecutorService`, `Executors.` or `CompletableFuture` (`CompletableFuture.completedFuture` is a value, not a thread).
- A test waits on the state it asserts; its hang guard is the suite's ceiling ([0139](0139-every-suite-ends-within-one-ceiling.md)).
- No line is exempt: a test needing a thread or a timeout is rewritten against a fake clock, a fake process or a mock executor, or deleted.
- `timing-in-tests` in `fitness.yml` counts the lines, `max 0`; `FitnessTest` pins that a sleep, a timeout or a thread fails it naming the file.

## Rejected

- A shorter timeout: it is still a clock the machine decides.
- A shared wait helper: a test shares a builder, a fake or a scanner only (0163).

## Reopen when

- The owner lifts the no-exemption order.
