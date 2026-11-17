# 0194 — The Session tab streams the window's terminal from tmux control mode

[← decisions](README.md) · spec: [`session-stream`](../../openspec/specs/session-stream/spec.md)

## Status

Accepted 2026-11-17; reopens [0060](0060-the-session-tab-stays-beside-focus.md)'s read of the tab, which stays only
as the peek under the other tabs.

## Context

- The tab read `capture-pane` every 5 s: it lagged, lost the cursor and took no keystroke.
- The server track makes the board the way to reach a session: a login or a reply is typed where the session is.
- Probed on tmux 3.8, 2026-11-17: a control client sees `%output <pane> <octal-escaped bytes>` for every pane, answers
  each command in a `%begin`/`%end` block in order, leaves the window size alone, and exits when its input closes.
- `resize-window -x -y` sets `window-size manual` on the window; `resize-window -A` sets it again after an unset.

## Decision

- Port `WindowStream` in `core/port/terminal`; its driver is `adapter/tmux` over `tmux -C attach`, one client per view.
- The first frame is the screen: `display-message` and `capture-pane` go down the same channel as the output, so what the
  screen holds is never sent twice and never lost; every `%output` after it is streamed.
- Typing is `send-keys -H` for ASCII and `-l` for the rest; the keys of the human, never a Master tool.
- Spring's WebSocket serves `/api/windows/stream`: it passes the auth chain and `LoopbackFilter`, and an upgrade counts as a
  write for `BoardWriteFilter`, so it needs the page's Origin.
- The window takes the view's size; the first view records its size and `window-size` policy, the last puts both back.
- A control client is no viewer: `list-clients` is read by `client_control_mode`, so a browser alone never hides the screen
  a human needs opened.
- `OpenedWindows.open` asks the window's state, no longer its screen.
- The port, the `spring-boot-starter-websocket` line and the `session-stream` spec raise the ceilings `ports` 50 to 51,
  `dependencyLinesInBuildGradle` 66 to 67 and `specCapabilities` 47 to 48.

## Rejected

- Polling faster: still no cursor and no typing, and a tmux process per tick per browser.
- `pipe-pane`: no screen at start, no size, no cursor.
- A pty running `tmux attach` per browser: a pty library for a job control mode does, and it shows the session's current
  window, not the task's.
- One shared control client fanned out: one size owner for several browsers, and state to rebuild per late joiner.

## Reopen when

- A browser falls behind a busy window and tmux's output buffer grows: `pause-after` flow control is then due.
- A second session host implements `WindowStream`: its size and typing rules join the port's contract.
- The tmux a supported OS ships has no control mode.
