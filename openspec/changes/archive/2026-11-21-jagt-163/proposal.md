## Why

The board, jagt's only surface, had no accessibility gate; axe-core found real WCAG failures on it.

## What Changes

- `boardTest` runs axe-core on the board's views and fails on a serious or critical violation.
- A row's name is its visible text, its unprinted facts its description; the palette button's name starts with its key.
- `--you` and `--ok` darken in the light theme to reach 4.5:1 on the chip background.

## Capabilities

### New Capabilities

### Modified Capabilities

- `board-app`: the accessibility gate.
