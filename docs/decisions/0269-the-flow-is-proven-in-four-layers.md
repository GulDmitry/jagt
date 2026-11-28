# 0269 — The flow is proven in four layers

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) ·
on [0260](0260-one-reconciler-owns-the-progress-of-every-in-flight-step.md),
[0261](0261-a-read-carries-its-generation-and-every-fault-meets-every-step.md)

## Status

Accepted 2026-11-28.

## Context

- Of 166 defect fixes since 2026-10-15, about 22 were session-seam reads and 9 restart repeats (estimate, by subject): wiring a model never sees.
- The model check runs the real chart and `Reconciler.decide` (`TaskChartProperties`, `ReconcilerProperties`); the simulation runs fakes at
  the ports (`FaultScheduleProperties`); one e2e row names CI_FAILED, none a retry.

## Decision

- **Model check**: every fault meets every step on every reachable configuration (0261); in `propertyTest`, part of `check`.
- **Simulation**: the real jobs over fakes at the ports, one seeded clock, every fault schedule to depth 3 for one task, a restart as a state
  reload between any two events; every trace is a path the model admits; in `propertyTest`.
- **End to end**: one `e2eTest` row per `StepPolicy` row with its worst fault, through real git, tmux, a scripted agent and the Spring wiring.
- **A model plays the bad session** against the real cycle per minor release and per refresh only.
- **A finding becomes a seeded case one layer down**: a played session's to an e2e row or a schedule, a schedule's to a fault move in the model.

## Rejected

- A second model beside the code (TLA+): it drifts from the chart; the explorer runs the real one.
- A played session per commit: it spends tokens and gates on a non-deterministic run.
- The cross-product in e2e: minutes per row; it stays in the simulation.

## Reopen when

- A defect passes the first three layers twice in one release, or depth 3 outgrows the commit gate's time.
