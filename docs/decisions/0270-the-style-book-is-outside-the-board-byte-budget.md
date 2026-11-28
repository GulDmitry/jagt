# 0270 — The style book is outside the board's byte budget, now 224 KB

[← decisions](README.md) · rule: [`board`](../../openspec/specs/board/spec.md)
on [0176](0176-every-kind-has-a-ceiling.md)

## Status

Accepted 2026-11-28. Replaces the 241 KB of [0265](0265-the-board-byte-budget-is-241-kb.md).

## Context

- The budget guards what a browser loads for the board; `styleguide.html` and `styleguide/` are a page of their own the board never loads.
- A card per status and per stop trigger, both themes and the legend grow the style book to 25030 B (2026-11-28);
  counted, they would break 0265's 116 B margin with no byte added to the board.
- Without the style book the board weighs 228384 B, measured 2026-11-28 the way `BoardAssetsTest` sums it.

## Decision

Why: a budget counts what it guards; the style book must grow with every state without crowding the board.

- `BoardAssetsTest` leaves `styleguide.html` and `styleguide/` out of the sum.
- The budget is 224 KB (229376 B): 992 B above the measured weight.

## Rejected

- Raising the budget to 266 KB: the board would gain 25 KB of room it never used.

## Reopen when

- The board's eager weight passes 224 KB, or the board itself loads a style-book file.
