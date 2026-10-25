# 0085 — Headless reads stop behind a circuit breaker

[← decisions](README.md) · rule: [`usage-limits`](../../openspec/specs/usage-limits/spec.md)

## Status

Accepted 2026-10-25. Mechanism superseded by [0086](0086-the-breaker-is-resilience4js.md).

## Context

- Every job calling a headless read retried on each tick through a provider outage, each call timing out or failing
  and logging, while nothing on the board said the assistant was down.
- A usage limit is another ending: `SessionWakes` already waits for the reset ([0050](0050-a-session-stopped-on-a-full-window-is-woken-once.md)).
  Its error word is a hook field the result envelope does not carry, so headless reads cannot share that recogniser.
- Measured on claude 2.1.308 against a local server answering 529: the CLI printed nothing for 181s, then its error
  envelope; with `CLAUDE_CODE_MAX_RETRIES=2`, 3s. The call's timeout is 180s.
- `HeadlessClaude` was at the five-collaborator ceiling, so the gate is one component the process run passes through.

## Decision

- `adapter/assistant/AssistantProcess` is the one place a headless CLI process runs; it asks `AssistantBreaker` first.
- Every headless call passes `CLAUDE_CODE_MAX_RETRIES=2`, so an outage ends inside the timeout with its own error.
- After `orchestrator.assistant.breakerFailures` (3) consecutive provider failures the breaker opens for
  `breakerCooldown` (5m); then one probe read runs, success closes it, failure re-opens it.
- A refused read throws `port/AssistantUnavailable`, an `IllegalStateException` naming the last provider error:
  jobs book it as a failed run, a surface shows its sentence, `PaidRead` does not retry it.
- `ProviderFault` reads the result envelope: `is_error` with `terminal_reason: api_error` and an `api_error_status`
  of 500 or more, or none (a refused connection), is a failure; 429 means the provider answered and never counts.
- A timeout, a spend ceiling, another status or no envelope says nothing and is ignored.
- `port/AssistantHealth` carries the outage to the board's header chip.

## Rejected

- A queue of refused reads: nothing grows, and a read refused is asked again by the next tick.
- Counting timeouts: a slow tracker MCP looks the same as a dead provider.
- Matching words in the result text: the envelope's own status says it.

## Reopen when

- An outage is seen not tripping the breaker, or a subscription limit is seen as a failure.
- A timeout is shown to be a provider failure on real runs.
