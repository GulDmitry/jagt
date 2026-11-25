# 0241 — The board's byte budget is 232 KB

[← decisions](README.md) · rule: [`board`](../../openspec/specs/board/spec.md), [`board-settings`](../../openspec/specs/board-settings/spec.md)
on [0141](0141-the-boards-words-are-catalog-files.md), [0176](0176-every-kind-has-a-ceiling.md)

## Status

Accepted 2026-11-24, by the owner's order (JAGT-265). Raises the 224 KB of 0141 and 0173.

## Context

- The board's scripts, styles and pages weighed about 228.9 KB before the settings dialog (`BoardAssetsTest`).
- The dialog, its choice control and its catalog keys add about 6 KB net: 234992 B, 5616 B over 224 KB.
- A lossless recompress of the favicon saves 376 B, too little to matter.

## Decision

Why: a settings dialog with sections is a board feature; the budget follows measured need, never a guess.

- The budget is 232 KB (237568 B), asserted in `BoardAssetsTest`.
- A later change raises it again only by its measured need, under this record (JAGT-267 reads it first).
- **Ceiling raised by name** ([0176](0176-every-kind-has-a-ceiling.md)): `spec-capabilities` 54 to 55, for `board-settings`.

## Rejected

- Cutting 5.6 KB from the dialog: it drops the sections and the ✓ mark the owner asked for.
- Loading the dialog on demand: the gear is on every page and the dialog applies the theme before the first paint.

## Reopen when

- The board's eager weight passes 232 KB, or a build step becomes cheaper than the bytes.
