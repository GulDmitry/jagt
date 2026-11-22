# 0055 — jagt speaks English and keeps UTC

[← decisions](README.md) · rule: [`docs/rules/style.md`](../rules/style.md#code-quality)

## Status

Accepted 2026-10-21. Amended by [0215](0215-the-clock-rules-read-test-code-too.md).

## Context

- The board formatted times with the browser's `toLocale*String`, the backend with `ZoneId.systemDefault()`: one task read
  at two hours on two screens.
- The suite ran in Turkish to trap a locale-bound case change, so it ran unlike production.

## Decision

- `OrchestratorApplication.main` sets `Locale.ENGLISH` and UTC first; the suite runs with `en`, `US` and `UTC` too.
- `RuntimeWiring.clock()` is `Clock.systemUTC()`, the one read of the time: no main code asks the machine for its clock, zone or locale.
- Two reads stay named in `fitness.yml`: `StandClock`, the stand harness waiting on real processes, and `EvalStats`, the
  stamp of an eval's stats line; both real time, neither testable by a frozen clock.
- Test code reads a fixed clock (`Clock.fixed` or `FakeClock`) everywhere, the rows scan every source set; named with
  why: `StandClockTest` (pins that `StandClock` reads the machine's time), the `boardTest` classes, `E2eWorkspace` and
  `MasterModeFlowTest` with its `Stalled` seed (a real server and browser answer to real time), `FakeOidcProvider` (its
  tokens are checked by a real server against the machine's clock).
- One module, `board/core/time.js`, formats every board time in `en-GB` UTC; the full one ends in `UTC`.
- `now-without-clock` and `BoardAssetsTest` fail the gate on a call that reads the machine or the browser.

## Rejected

- The Turkish trap: tests unlike production prove nothing about it.
- The browser's local time on the board: two humans on one task would read two clocks.
- A JVM flag: jagt starts with none ([0039](0039-jagt-starts-with-no-jvm-flag.md)).

## Reopen when

- A user needs another language or zone, as a decision of its own.
