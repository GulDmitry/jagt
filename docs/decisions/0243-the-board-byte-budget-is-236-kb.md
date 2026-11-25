# 0243 — The board's byte budget is 236 KB

[← decisions](README.md) · rule: [`board`](../../openspec/specs/board/spec.md)
on [0141](0141-the-boards-words-are-catalog-files.md), [0176](0176-every-kind-has-a-ceiling.md)

## Status

Accepted 2026-11-25 (JAGT-267). Raises the 232 KB of [0241](0241-the-board-byte-budget-is-232-kb.md).

## Context

- One `LogLines` component, its parser and the styles for every log view weigh 3088 B more than 0241's budget allows:
  240656 B measured on 2026-11-25 (`BoardAssetsTest`), 237568 B allowed.
- The catalog text counts nothing: the test sums scripts, styles and pages.

## Decision

Why: the log views share one component and one parser; the budget follows the measured need.

- The budget is 236 KB (241664 B), asserted in `BoardAssetsTest`: 1008 B above the measured weight.
- A later change raises it again only by its measured need, under this record.

## Rejected

- One component per log view: three copies of the parser and the styles weigh more than the shared one.

## Reopen when

- The board's eager weight passes 236 KB, or a build step becomes cheaper than the bytes.
