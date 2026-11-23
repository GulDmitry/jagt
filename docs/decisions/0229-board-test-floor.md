# 0229 — The board's test floor

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · kind: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-kinds) ·
on [0162](0162-a-check-holds-for-all-code.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-23.

## Context

- The 2026-11-22 investigation found c8 gating 17 of 112 board files (about 17% of lines) and no accessibility check.
- Unit tests cover the logic modules; the DOM modules (`ui/`, `components/`) run only in a browser.
- `boardTest` measured 97.7% lines and 91.4% branches in the browser on 2026-11-22.
- The first Stryker run on the covered set scored 95.82% in 834 s on 2026-11-23 (local, loaded machine).

## Decision

- **Unit floor**: c8 gates every file of `.c8rc.json`'s include list per file, lines and branches; a scope test fails a file
  in neither the list nor a reasoned exclusion.
- **Browser floor**: `boardTest` collects V8 coverage through Playwright, c8 merges it with the unit lcov, and each file keeps its
  floor in `board/coverage-floors/floors.json`, the first measured value rounded down, no margin.
- **Accessibility**: `boardTest` runs axe-core (a pinned devDependency) on every main view and fails on a serious or critical
  violation, naming rule and node.
- **Mutation**: the nightly runs `npm run mutate` on the covered set; a score under `thresholds.break` (95, the first score
  rounded down) fails the job.

## Rejected

- Stryker on every push: 14 minutes, a nightly's budget.
- DOM stubs to unit-test `ui/`: a second, weaker model of the browser.
- A floor margin: a timing flake goes to the flaky-test route, never a lower number.

## Reopen when

- The mutation run passes 30 minutes: shard it or narrow the set.
- A standard replaces axe-core or V8 coverage for browser JavaScript.
