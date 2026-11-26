# 0258 — The eight pitest tasks run without a Gradle time limit

[← decisions](README.md) · on [0139](0139-every-suite-ends-within-one-ceiling.md), [0176](0176-every-kind-has-a-ceiling.md) ·
spec: [`fitness`](../../openspec/specs/fitness/spec.md)

## Status

Superseded 2026-11-26 by [0259](0259-each-pitest-task-stops-at-three-times-its-measured-run.md).

## Context

- `unlimited-runs` lists the test and gate tasks declaring no `timeout`; on 2026-11-26 it measured 8, all `pitest`:
  `:adapter`, `:core`, `:mutation-scope`, root, `:stand`, `:statechart`, `:surface`, `:usecase`.
- The row's ceiling was 8 with no record; a tool-measured count now ratchets (`Report`), so it stays at 8 by this record.
- No wall-clock time of a `pitest` run is measured: unknown, so no limit can be set from it (0139 sets limits from measured time).

## Decision

- The ceiling is 8, the measured count, and only goes down.
- PIT bounds each mutant by its own timeout factor and constant; a whole run is bounded by the invoking gate's `timeout --foreground`.

## Reopens when

- A `pitest` run's time is measured: each task then gets a limit and the ceiling falls to 0.
