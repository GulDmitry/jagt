# 0172 — Readiness is Spring Boot Actuator's probe

[← decisions](README.md) · rule: [`readiness`](../../openspec/specs/readiness/spec.md#requirement-pod-readiness)

## Status

Accepted 2026-11-16 by the owner's order: a pod is routed to only once jagt is usable.

## Context

- The Kubernetes example probed `/login.html`, which answers as soon as the web server listens, before the start's checks ran.
- Boot publishes `ReadinessState` itself: refusing until the start finished, accepting once it did.
- jagt held no readiness signal; a project clone runs on the start and again each minute for a failed one.

## Decision

- `spring-boot-starter-actuator` is in `:surface`; only `health` is exposed, with probes enabled.
- The `readiness` group is `readinessState` plus `clones`: `ClonesHealth` is out of service while `ProjectClonesJob` runs a clone.
- `/actuator/health/readiness` is a public path in `contracts/openapi.yaml`, so no login is asked of a kubelet.
- A clone that failed does not hold the pod unready: the board's head shows it, and an unready pod hides the board.

## Rejected

- A readiness endpoint of jagt's own: Boot's group, state and JSON are the standard one.
- Publishing `AvailabilityChangeEvent` from the job: a start-time race, and the state would have two writers.

## Reopen when

- A second condition must hold a pod unready: it joins the group as one more indicator.
