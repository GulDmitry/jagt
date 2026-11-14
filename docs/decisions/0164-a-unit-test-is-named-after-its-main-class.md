# 0164 — A unit test is named after its main class

[← decisions](README.md) · rule: [`testing`](../rules/testing.md) · amends [0163](0163-a-test-shares-a-builder-a-fake-or-a-scanner.md)

## Status

Accepted 2026-11-14.

## Context

- Owner 2026-11-11: a test class that names no main class hides what it pins; 118 of 656 named none.
- Rule tests (0162, 0163) now freeze what breaks a test rule, so a breach can land before every old one is fixed.

## Decision

- A module's unit test is `<MainClass>Test`, or `<MainClass><Aspect>Test`, in the main class's package.
- Exempt by kind, never by a list: the root module's rule tests and the suite sets (boardTest, e2e, evals, stand,
  linuxTest).
- `TestNamingTest` checks it; its ArchUnit freeze store holds today's breaches and only shrinks.

## Rejected

- An exemption list: a list grows; a kind does not.
- Fixing every breach before the rule: the rule would wait on 34 renames and merges.

## Reopen when

- A suite set gains unit tests of a main class.
