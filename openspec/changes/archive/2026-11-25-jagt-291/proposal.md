## Why

The board's alias sort followed the browser's locale, one chip and one sentence sat outside the catalogs, and legend rows were keyed by position.

## What Changes

- Alias order is the English numeric one in every browser.
- Every text the board shows is a catalog key; a test fails on a key asked for and missing, or held and never asked for.
- Each legend row carries its own catalog key.

## Capabilities

### New Capabilities

### Modified Capabilities

- `board`: sorting and formatting never read the browser's locale; the catalogs and their asked keys agree.
