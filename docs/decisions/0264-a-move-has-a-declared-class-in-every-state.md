# 0264 — A move has a declared class in every state

[← decisions](README.md) · rule: [`task-flow`](../../openspec/specs/task-flow/spec.md) ·
on [0260](0260-one-reconciler-owns-the-progress-of-every-in-flight-step.md),
[0261](0261-a-read-carries-its-generation-and-every-fault-meets-every-step.md)

## Status

Accepted 2026-11-27.

## Context

- A move with no edge for a state came back empty from `TaskChart.effects`, and `readLands` and `deployReadLands` ended in `.orElse(from)`:
  a move left out on purpose and one forgotten read the same, so a forgotten pair surfaced only as a wrong status in the field.
- Seven past status-landing defects came from such a pair (estimate, by subject).
- 0261 made fault by step total in the model check; the chart itself had no table of its own.

## Decision

- **Every kind of move names the regions it concerns** (`flow/MoveClasses`, a role's own `declareRegion` for its region). In each state of those
  regions the kind is taken by an edge, ignored with its why, or refused under a name; `:statechart` fails the build on a state with none,
  naming `move X has no class in state region.STATE`, and on an edge in a region the kind does not concern.
- **A declaration and an edge never share a state**, a declaration carries its why, and a kind is addressed to a region by a declaration,
  never by an edge alone; a role declares the kinds it adds, so a chart without it classifies nothing for them.
- **An edge names its kind** (`onMatching(region, from, kind, label, matches)`); a supertype stands for every leaf under it, so an edge on any move
  (a checker's read closing when the deployed change leaves) takes every kind where it stands.
- **Reads answer empty where the chart ignores them**: `readLands` and `deployReadLands` return `Optional`, no `.orElse(from)`.
- **In production** a fact move with no class logs `move unclassified` and marks the board's head (`UnclassifiedMoves`), jagt's own reach failing
  being the board's; a claim or a press (`TaskMove.Asked`) is refused. The mark ends on restart, the chart being read again.
  After the build check the case is unreachable but for a kind outside `TaskMove`; the guard stays so a missed one is loud.
- **The explorer asserts it** on every reachable configuration and every move.
- **A boundary row** (`unknown-words-are-named`): no `default ->` and no `.orElse(` in `protocol/`, `port/assistant` and the answer parsers.
  Five stand today, so the row is a ratchet at 5, falling as each becomes a named violation, never a zero.

## Rejected

- Classes per region and state only, no kind scope: 47 kinds against some fifteen regions is about 700 lines nobody reads, each a silent
  blanket for the next region; the scope table is the one place a region is added to a kind.
- One class per edge guard (a status pair): the granularity of a kind per state is what the defects missed; guards stay modelled conditions.

## Reopens when

- A defect lands through a pair the kind-per-state grain cannot name, or the scope table drifts from the edges without the build saying so.
