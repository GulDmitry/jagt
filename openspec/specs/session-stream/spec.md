# session-stream Specification

## Purpose

The Session tab is a live terminal.
A window's output streams to the board, a human's keys reach the window, and the window takes the tab's size.

## Requirements

### Requirement: A window's terminal streams to the board
The Session tab SHALL show a window's terminal as it prints, over a WebSocket at `/api/windows/stream?task=<id>` or `?name=<window>`.
The server reads it from tmux control mode, one client per browser, and sends the screen first, then every byte after it. What the human
types in the tab reaches the window, every key included. The tab's library loads when the tab first opens, never with the page.
#### Scenario: Opening the tab
- **WHEN** the human opens the Session tab of a working session
- **THEN** its terminal shows the screen as it is and follows what the session prints
- **WHEN** the human types in it
- **THEN** the keys reach the session's CLI
#### Scenario: Two browsers
- **WHEN** two browsers open the same session's tab
- **THEN** both show what it prints, and either may type
#### Scenario: A window that is gone
- **WHEN** no window holds the session
- **THEN** the socket closes with code 4404 and the tab says no window holds the session
#### Scenario: Colour and cursor
- **WHEN** the window shows styled text with the cursor mid-screen
- **THEN** the first frame carries the styles and puts the cursor there

### Requirement: The stream is the human's door and is guarded
The WebSocket SHALL pass `LoopbackFilter`, carry the page's Origin like any write, and be refused without a login where `orchestrator.auth`
is `password` or `oidc`. No Master tool types through it: `Keys` and `run` stay verbs.
#### Scenario: No login
- **WHEN** auth is `password` and the WebSocket is opened without a login
- **THEN** it is refused with 401
#### Scenario: No Origin
- **WHEN** the upgrade carries no Origin
- **THEN** it is refused with 403

### Requirement: The browser sizes the window while the tab is open
The window SHALL take the size of the open tab's terminal.
The size it had before SHALL be restored when the last view of it closes.
#### Scenario: Closing the tab
- **WHEN** the human leaves the Session tab, or the page closes
- **THEN** the window is the size it was before the tab opened, and its size policy is the one it had
#### Scenario: Two views
- **WHEN** two views of one window are open and one closes
- **THEN** the window keeps the open view's size until the last closes

### Requirement: A browser's client is no screen
A tmux control client SHALL not count as a viewer: with only browsers attached, opening a task's window still opens a screen for the human.
#### Scenario: Only a browser is attached
- **WHEN** a task's window is opened and the only client is a browser's view
- **THEN** the terminal driver opens a viewer
