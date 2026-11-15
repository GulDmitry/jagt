# 0166 — Packages are architecture

[← decisions](README.md) · map: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#the-package-map) · builds on [0162](0162-a-check-holds-for-all-code.md)

## Status

Accepted 2026-11-14.

## Context

- Owner 2026-11-11: `flow/`, `service/`, `job/` and `adapter/` held 60 to 140 classes flat; the tree did not show
  layers, features or what each adapter adapts.
- The move put every module into sub-packages by feature and kind; six packages still hold more than ten classes
  because package-private partners cannot move without being widened.
- Without a check the next feature refills a flat package, and a package that is not on the map is an unnamed kind.

## Decision

- A new kind of thing is named in the package map before its first class.
- Once three classes share a kind or a feature they live in their own sub-package; nothing is decomposed ahead of that.
- A package holds ten classes directly at most and never a single one.
- A package-private partner that cannot move without widening stays with its package; the map lists it as an
  exception with its why.
- `PackagesTest` asserts the map and the counts: every package on disk is in the map and every listed package exists,
  no package is over ten classes or a single class outside the map's exception lists.
- Three of a kind is a review rule: no class declares its kind, so a name-suffix test would flag the kind's own package.

## Rejected

- Widening a package-private type to empty `flow/` or `service/`: the visibility is the design, a move is not.
- Listing exceptions inside the test: the map is where a reader looks, and one list cannot drift from the other.

## Reopen when

- An exception's partners move or are widened: its row goes in the same commit.
- Ten classes in one package keep splitting by something other than kind or feature.
