# 0255 — The board's byte budget is 240 KB

[← decisions](README.md) · rule: [`board`](../../openspec/specs/board/spec.md)
on [0141](0141-the-boards-words-are-catalog-files.md), [0176](0176-every-kind-has-a-ceiling.md)

## Status

Accepted 2026-11-26. Raises the 236 KB of [0243](0243-the-board-byte-budget-is-236-kb.md).

## Context

- The stop kinds add their marks to the cards, the list, the legend and the styles: 2308 B more than 0243's budget allows:
  243972 B measured on 2026-11-26 (`BoardAssetsTest`), 241664 B allowed.
- The catalog text counts nothing: the test sums scripts, styles and pages.

## Decision

Why: one mark per meaning is the board's own rule ([`design`](../rules/design.md)); the budget follows the measured need.

- The budget is 240 KB (245760 B), asserted in `BoardAssetsTest`: 1788 B above the measured weight.
- A later change raises it again only by its measured need, under this record.

## Rejected

- Cutting the legend samples: every mark has one legend row (AGENTS.md "Interfaces").

## Reopen when

- The board's eager weight passes 240 KB, or a build step becomes cheaper than the bytes.
