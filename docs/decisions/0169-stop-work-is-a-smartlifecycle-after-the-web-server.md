# 0169 — Stop work is a SmartLifecycle after the web server

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md) "Code" · beside
[0160](0160-the-chart-is-an-instance-and-start-work-is-an-event.md)

## Status

Accepted 2026-11-15.

## Context

- With `onStop: end`, `ShutdownEndings` ended every session on `ContextClosedEvent`.
- That event fires before the web server stops taking requests, so a start request in the window began a session after the endings.
- Lifecycle beans stop in descending phase order; the web server's graceful shutdown stands at `DEFAULT_PHASE - 1024`.

## Decision

- **Work at stop is a `SmartLifecycle` whose phase is below the web server's**; it runs once no request can arrive.
- `ShutdownEndings` is that bean (`DEFAULT_PHASE - 2048`); its `stop()` ends the live sessions.
- Start work stays an event or a `Job` ([0160](0160-the-chart-is-an-instance-and-start-work-is-an-event.md)); `@PostConstruct` stays banned.
- `LifecycleHooksTest` bans `@PostConstruct` only, so it stays unchanged.

## Rejected

- A "stopping" flag set on `ContextClosedEvent` and checked by every start path: touches each path, and a request already past the check still starts.
- A second `ContextClosedEvent` listener ordered later: the event precedes the web server's stop whatever the order.

## Reopen when

- Spring Boot changes the web server's lifecycle phase; the regression test fails first.

Sources checked 2026-11-15: Spring Framework 7 `SmartLifecycle` phase ordering; Spring Boot graceful shutdown reference.
