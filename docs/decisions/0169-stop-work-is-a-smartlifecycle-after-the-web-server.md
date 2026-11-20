# 0169 — Stop work is a SmartLifecycle after the web server

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md) "Code" · beside
[0160](0160-the-chart-is-an-instance-and-start-work-is-an-event.md)

## Status

Accepted 2026-11-15.

## Context

- With `onStop: end`, `ShutdownEndings` ended every session on `ContextClosedEvent`.
- That event fires before the web server stops taking requests, so a start request in the window began a session after the endings.
- Lifecycle beans stop in descending phase order.
- Spring Boot 4.1.1: the web server's graceful shutdown stands at `DEFAULT_PHASE - 1024`.
- Its start-stop lifecycle, which stops taking requests, stands at `DEFAULT_PHASE - 2048`.
- The first value, `DEFAULT_PHASE - 2048`, equalled that phase: a start in the drain could sign a session after the endings.

## Decision

- **Work at stop is a `SmartLifecycle` whose phase is below the web server's**; it runs once no request can arrive.
- `ShutdownEndings` is that bean (`DEFAULT_PHASE - 3072`, below the start-stop phase)
- The usecase module sees no web-server constant, so the number is literal.; its `stop()` ends the live sessions.
- Start work stays an event or a `Job` ([0160](0160-the-chart-is-an-instance-and-start-work-is-an-event.md)); `@PostConstruct` stays banned.
- `post-construct-hooks` bans `@PostConstruct` only, so it stays unchanged.

## Rejected

- A "stopping" flag set on `ContextClosedEvent` and checked by every start path: touches each path, and a request already past the check still starts.
- A second `ContextClosedEvent` listener ordered later: the event precedes the web server's stop whatever the order.

## Reopen when

- Spring Boot changes the web server's lifecycle phases; the regression test fails first.

Sources checked 2026-11-15: Spring Framework 7 `SmartLifecycle` ordering; Spring Boot graceful shutdown reference;
spring-boot-web-server 4.1.1 phase constants.
