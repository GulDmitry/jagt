# 0218 — Metrics stay at /actuator/metrics, unexported

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md) · relates: [0172](0172-readiness-is-spring-boot-actuators-probe.md),
[0087](0087-the-industry-standard-library-before-our-own-code.md)

## Status

Accepted 2026-11-21. Reopens on the condition below.

## Context

- `management.endpoints.web.exposure.include` is `health,metrics,info`; metrics and info answer the owner only (login, else loopback).
- No Micrometer registry and no tracing bridge is on the classpath: nothing is pushed or scraped.
- 0087 asks for a decision where the industry has a standard and jagt does not take it.
- One owner runs one jagt; the only reader of a metric is that owner, polling the endpoint.

## Decision

- Metrics stay Spring Boot Actuator's `/actuator/metrics`, read by polling it.
- No registry (Prometheus, OTLP or other) and no tracing is added while no deployment has a collector to scrape it.
- A registry is a seam chosen in `jagt.yml` ([0130](0130-every-third-party-is-a-configured-seam.md)), never a default.

## Rejected

- A Prometheus registry now: a public or scrapable endpoint for no reader, widening the contract's public paths.
- An OTLP push or tracing now: a vendor endpoint to configure and a failure mode (collector down) for no consumer.

## Reopen when

- A server deployment ([`docs/server.md`](../server.md)) runs under a monitoring stack that needs to scrape or receive metrics.
- A second reader than the owner needs a metric the endpoint cannot give by polling.
