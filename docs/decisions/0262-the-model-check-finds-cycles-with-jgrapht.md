# 0262 — The model check finds cycles with JGraphT

[← decisions](README.md) · on [0260](0260-one-reconciler-owns-the-progress-of-every-in-flight-step.md),
[0252](0252-build-logic-reads-yaml-with-snakeyaml.md)

## Status

Accepted 2026-11-26.

## Context

- 0260's bounded liveness needs cycle and longest-path search over the reachable states waiting on a tick.

## Decision

- `core` declares `org.jgrapht:jgrapht-core` in test scope; `dependency-lines-in-build-gradle` rises from 99 to 100.

## Rejected

- A cycle search of our own: own code for a job a standard library does.

## Reopen when

- The model check leaves `core`'s tests, or JGraphT stops shipping releases.
