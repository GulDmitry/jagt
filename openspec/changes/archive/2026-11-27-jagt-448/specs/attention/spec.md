## MODIFIED Requirements

### Requirement: A window jagt opens unasked never takes focus
A session-host window (a task's, a command or a shell one) and a viewer tab jagt opens unasked SHALL open without taking focus: the window
detached, the kitty tab with `--keep-focus`, the Ghostty tab with the previously selected tab reselected.
Only `focusTaskWindow` and `reveal` SHALL select a window or raise the viewer; the Login the human presses selects its own window.

#### Scenario: A session starts while the human works in another
- **WHEN** the human works in task `a1`'s session and jagt restarts task `a2`'s session or opens its own window
- **THEN** `a2`'s window or tab opens without taking focus and `a1` stays in front
- **WHEN** the human presses Focus on `a2`
- **THEN** `a2`'s window comes to the front
