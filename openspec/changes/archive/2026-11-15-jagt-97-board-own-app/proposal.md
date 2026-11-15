## Why

The board is a front-end app, the backend another; the backend named board files and the board's own catalogs.

## What Changes

- The board lives at `board/` in the repository, apart from the backend.
- `contracts/` holds their one hand-written coupling: routes and events.
- The jar carries a copy of `board/`; `orchestrator.board.dir` serves a folder instead, relative to the root.
- The board owns its language catalogs; `<root>/i18n` is served as plain files and merged by the board.
- The login's public paths come from the contract.

## Capabilities

### New Capabilities

- `board-app`: the board's own app, the contract, its folder, its catalogs and the user's overlay.

### Modified Capabilities

- `board`: the board is an app of its own at `board/`; its vendored files are served from there.
