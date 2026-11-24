## ADDED Requirements

### Requirement: A window jagt opens unasked never takes focus
A task's session-host window and a viewer tab opened for it SHALL open without taking focus: the window detached, the kitty tab with
`--keep-focus`, the Ghostty tab with the previously selected tab reselected. Only `focusTaskWindow` and `reveal` SHALL select a window or raise
the viewer.

#### Scenario: A session starts while the human works in another
- **WHEN** the human works in task `a1`'s session and jagt restarts task `a2`'s session
- **THEN** `a2`'s window or tab opens without taking focus and `a1` stays in front
- **WHEN** the human presses Focus on `a2`
- **THEN** `a2`'s window comes to the front
