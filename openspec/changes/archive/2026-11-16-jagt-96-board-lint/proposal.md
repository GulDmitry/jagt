## Why

Prettier formats the board; its output alone passes the 160 KB budget, so the budget rises to 192 KB (owner 2026-11-16).

## What Changes

- The board's code and styles weigh under 192 KB.
- `board/` carries Prettier and ESLint, run by `npm run lint`.

## Capabilities

### Modified Capabilities

- `board`: the byte budget.
