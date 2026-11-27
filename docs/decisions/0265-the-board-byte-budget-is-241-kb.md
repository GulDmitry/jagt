# 0265 — The board's byte budget is 241 KB

[← decisions](README.md) · rule: [`board`](../../openspec/specs/board/spec.md)
on [0141](0141-the-boards-words-are-catalog-files.md), [0176](0176-every-kind-has-a-ceiling.md)

## Status

Accepted 2026-11-27. Raises the 240 KB of [0255](0255-the-board-byte-budget-is-240-kb.md).

## Context

- Telling a failed render from a failed read splits `refresh.js` into three units, each under the five-import ceiling:
  246668 B measured on 2026-11-27 (`BoardAssetsTest`), 245760 B allowed.

## Decision

Why: a render failure must not read as a down backend; the budget follows the measured need ([0255](0255-the-board-byte-budget-is-240-kb.md)).

- The budget is 241 KB (246784 B), asserted in `BoardAssetsTest`: 116 B above the measured weight.

## Rejected

- One `refresh.js` with an import-ceiling suppression: a suppression needs a reason, zero is the target, and it saves only 400 B.

## Reopen when

- The board's eager weight passes 241 KB, or a build step becomes cheaper than the bytes.
