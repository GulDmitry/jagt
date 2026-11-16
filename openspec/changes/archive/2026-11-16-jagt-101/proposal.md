## Why
`terminal: none` and `openTerminalWindow: false` both meant no window: two controls for one question.

## What Changes
- `terminal: none` is the one key for no terminal window; it needs no kitty.
- `openTerminalWindow` is retired: startup refuses it and names `terminal: none`.
- A kitty or ghostty driver always opens its viewer.
