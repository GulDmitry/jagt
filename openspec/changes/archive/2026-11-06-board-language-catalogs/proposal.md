## Why

The board spoke English alone, and a human reading another language had no way to change that.

## What Changes

- The gear also chooses the language, one entry per catalog file present: the jar's own and any in `<root>/i18n/`.
- Only the board's own UI text comes from a catalog; English is the default and fills any key a catalog lacks.
- Polish and Russian ship as the first two catalogs.

## Capabilities

### Modified Capabilities

- `board`: the gear holds the language beside the theme.
