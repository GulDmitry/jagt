## Why

kitty was the only viewer. Ghostty 1.3 on macOS addresses windows and tabs through AppleScript, as kitty does through remote control.

## What Changes

- `orchestrator.terminal` (`kitty` default, `ghostty`) selects the `TerminalDriver`.
- `GhosttyTerminalDriver` opens a tab in the window carrying the viewer title, reveals and closes it.
- Ghostty on a non-macOS host is refused at startup.

## Capabilities

### Modified Capabilities

- `platform`: the viewer driver is chosen by `orchestrator.terminal`.
