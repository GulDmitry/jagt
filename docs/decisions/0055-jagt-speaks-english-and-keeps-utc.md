# 0055 — jagt speaks English and keeps UTC

[← decisions](README.md) · rule: [`docs/rules/style.md`](../rules/style.md#code-quality)

## Status

Accepted 2026-10-21.

## Context

- The board formatted times with the browser's `toLocale*String`, the backend with `ZoneId.systemDefault()`: one task read
  at two hours on two screens.
- The suite ran in Turkish to trap a locale-bound case change, so it ran unlike production.

## Decision

- `OrchestratorApplication.main` sets `Locale.ENGLISH` and UTC first; the suite runs with `en`, `US` and `UTC` too.
- `RuntimeWiring.clock()` is `Clock.systemUTC()`; no main code asks the machine for a zone, a locale or the local time.
- One module, `static/core/time.js`, formats every board time in `en-GB` UTC; the full one ends in `UTC`.
- `EnglishAndUtcTest` and `BoardAssetsTest` fail the gate on a call that reads the machine or the browser.

## Rejected

- The Turkish trap: tests unlike production prove nothing about it.
- The browser's local time on the board: two humans on one task would read two clocks.
- A JVM flag: jagt starts with none ([0039](0039-jagt-starts-with-no-jvm-flag.md)).

## Reopen when

- A user needs another language or zone, as a decision of its own.
