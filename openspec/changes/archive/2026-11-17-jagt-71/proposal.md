## Why

The Session tab read the window's screen every 5 s: it lagged, held no cursor, and could not be typed into.

## What Changes

- The Session tab is a live terminal (xterm.js) fed by tmux control mode over a WebSocket under the board's guards.
- The browser sizes the window while the tab is open; the size is restored on close.
- Two browsers both see the stream and may both type; typing is the human's alone.
- `OpenedWindows.open` asks the window's state, not its screen.
- The 5 s peek of the last screen line under the other tabs stays.

## Impact

- `session-stream` (new), `board-workspace`, `board`
- Decisions 0046 (vendor limit) and 0060 (the tab's read) are reopened by new records.
