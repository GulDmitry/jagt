# 0086 — The breaker is Resilience4j's

[← decisions](README.md) · rule: [`usage-limits`](../../openspec/specs/usage-limits/spec.md) ·
supersedes the mechanism of [0085](0085-headless-reads-stop-behind-a-circuit-breaker.md)

## Status

Accepted 2026-10-25. Resilience4j 2.4.0, current on Maven Central 2026-10-25.

## Context

- 0085 chose when the breaker opens, what a failure is and what a refusal says; its state machine was hand-written.
- A state machine is not jagt's own: Resilience4j's `CircuitBreaker` is the industry standard for it.
- Since 2.3.0 its config takes a `Clock`, so `EnglishAndUtcTest` holds.

## Decision

- `AssistantBreaker` wraps a Resilience4j `CircuitBreaker`; the library is named in `adapter/` alone.
- `breakerFailures` is a count-based window of that size, minimum calls equal to it, failure threshold 100%:
  it opens only when the last N calls all failed, which is N consecutive.
- `breakerCooldown` is `waitDurationInOpenState`, one permitted call in half-open.
- `ProviderFault` still decides what counts: only its `FAILED` reaches the breaker as an error, `REACHED` as a success,
  anything else releases the permit and counts as neither.
- A refused permit throws `port/AssistantUnavailable` with the last provider error; `AssistantHealth` reads the state
  and the next probe time recorded at the transition to open.
- Each transition is one structured log event with its cause, from the library's event publisher.
- One difference: the probe is admitted strictly after the cool-down, not at it, one millisecond later.

## Rejected

- The hand-written machine: the same behaviour, our code to keep correct and a thing a newcomer must read.
- The Spring Boot starter: it wants a registry and properties jagt's one breaker does not use.

## Reopen when

- Resilience4j drops the clock config or a count-based window stops meaning the last N calls.
- A second breaker appears and a registry earns its place.
