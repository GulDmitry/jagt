# 0087 — The industry-standard library before our own code

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md) · relates: [0086](0086-the-breaker-is-resilience4js.md)

## Status

Accepted 2026-10-25.

## Context

- A library that thousands of companies run is tested, maintained and read by more people than jagt's own code can be.
- Evidence kept: the circuit breaker was hand-written with its own tests and edge cases, and the library replaced it
  with the same behaviour bar one millisecond ([0086](0086-the-breaker-is-resilience4js.md)).
- Already so: Jackson for JSON, Spring for wiring, Preact for the board ([0046](0046-the-board-renders-preact-components-with-no-build.md)).

## Decision

- For a job with an industry-standard library (resilience, logging, JSON, a protocol's official SDK), jagt uses it.
- Own code is only what is jagt's own: its rules, its flow, its seams.
- Where jagt still writes the job itself, a decision records why the library does not fit.
- A library is named in `adapter/` or `surface/` alone; `core/` imports none new.

## Exceptions

- The MCP server stays hand-rolled (checked 2026-10-25): MCP Java SDK 2.1.0 supports Jackson 3, but its Spring WebMVC
  transport moved to Spring AI 2.x (Boot 4 compatibility unverified); the stateless servlet transport cannot answer
  jagt's 401 with the `-32001` `UNKNOWN_CALLER` body; `addToolFilter` covers `tools/list`, not `tools/call`.
  Reopen: a Spring AI line for Boot 4 with `mcp-spring-webmvc`, or the SDK gaining a request-rejection hook with a
  JSON-RPC body and call-time filtering.
- `LogFileRoll` stays (Logback 1.5.38, checked 2026-10-25): no roll-on-startup policy, and a custom triggering policy
  would still need jagt's another-instance-holds-the-port guard and the stray `.N` sweep.
  Reopen: Logback ships an on-startup trigger.

## Rejected

- Any library: only the standard one for the job; a niche one is a bet jagt carries.
- Own code kept because it already works: working is the cost of keeping it, not a reason.

## Reopen when

- A standard library is shown unfit on real runs: a measured cost, a missing hook, an abandoned project.
- A library forces a name or a dependency into `core/`.
