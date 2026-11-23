# 0228 — A class stays under a thousand lines

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md) "Code" · kind: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-kinds) ·
on [0162](0162-a-check-holds-for-all-code.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-22.

## Context

- Eleven files are over 1000 lines on `release` (counted 2026-11-22): three in `src/main` (`TaskState` 1375, `TaskChart` 1327,
  `ConfigService` 1012) and eight in `src/test`, the largest 1462.
- PMD's `ExcessiveClassLength` reports a class from 1000 lines up: its rule class sets the `minimum` property to 1000
  (`ExcessiveClassLengthRule`, tag `pmd_releases/6.55.0`, checked 2026-11-22).
- PMD deprecated that rule in 6.53.0 and removed it in 7.0.0, since lines of code is a noisy metric; `NcssCount` replaces it.
- jagt keeps lines: the check is cheap, needs no parser and reads the same for a test class as for a main class.

## Decision

- **No class in `src/main` or `src/test` of any module or source set passes 1000 lines.**
- **Row `class-lines`** (scan, `set: all`, `per: file`, `measure: lines`, max 1000) names each file over it with its count.
- **Its `except` list holds each file still over 1000**, each ending when the class is cut:
  `TaskState` at JAGT-198 step 2 (its region cut); the other ten at a cut of their own.
- **Row `class-lines-stale-exceptions`** reads the same list as a floor of 1001: an excepted class at or under 1000 lines fails,
  so the list only shrinks.
- **The scan engine's size rows honour a `min`**: they report the smallest unit and name each one below the floor.

## Rejected

- `NcssCount` (PMD 7): a second metric for the same question, and it needs the PMD ruleset beside a plain count.
- A limit above 1000: PMD's own default is where a class is called excessive.
- An exception list with no stale check: a cut class would keep its exemption.

## Reopen when

- A class stays under 1000 lines yet holds several responsibilities: a measure of cohesion replaces the count.
