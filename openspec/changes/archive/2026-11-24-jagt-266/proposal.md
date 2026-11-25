## Why

Opening, restarting or reattaching a task's session brought its window or tab to the front, taking focus from the task the human was working in.

## What Changes

- A task's tmux window opens detached, so the viewer keeps showing the window it showed.
- A kitty tab opens with `--keep-focus`.
- A Ghostty tab opens and the tab selected before stays selected.
- Only the Focus verb selects a window or raises the viewer.

## Capabilities

### New Capabilities

### Modified Capabilities

- `attention`: a window or tab jagt opens unasked never takes focus.
