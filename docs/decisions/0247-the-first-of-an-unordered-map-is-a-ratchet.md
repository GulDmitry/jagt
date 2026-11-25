# 0247 — The first element of an unordered map is a ratchet

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md) "Code" · kind: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-kinds) ·
on [0162](0162-a-check-holds-for-all-code.md), [0180](0180-each-spheres-industry-standard-is-jagts-floor.md)

## Status

Accepted 2026-11-25.

## Context

- `Map.copyOf` and `Map.of` iterate in an order that changes per JVM run (Javadoc of `Map.of`: iteration order is unspecified,
  checked 2026-11-25).
- A report naming several requests linked a different one after each restart, because the pick took `values().iterator().next()`.
- `LinkedHashMap` keeps insertion order, which the Java 21 `SequencedMap` contract names.

## Decision

- **Main code never takes the first element of a map's `values()`, `keySet()` or `entrySet()`** with `iterator().next()`,
  `stream().findFirst()` or `findAny()`.
- **Row `first-of-unordered-map`** (scan, `set: main`, `per: match`, max 2) counts each use; the two left are
  `ProjectRouting` and `RequestProject`, and the number only falls.
- **A pick follows an ordered key list first** (the task's repositories), then the order the map was written in:
  a map a message carries is copied into a `LinkedHashMap` and wrapped unmodifiable.

## Rejected

- Max 0 now: the two remaining uses need their own cause read, not a blind change.
- `SequencedMap` as a record component type: every caller building a `Map.of` would break for no gain.

## Reopen when

- A use passes the regex by another spelling (a loop returning on its first turn): the row reads syntax, not intent.
