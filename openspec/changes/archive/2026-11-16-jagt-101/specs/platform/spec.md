## MODIFIED Requirements

### Requirement: The viewer driver is chosen by orchestrator.terminal
`orchestrator.terminal` SHALL select the `TerminalDriver`: `kitty` (default), `ghostty` or `none`. `GhosttyTerminalDriver` SHALL
drive Ghostty through its AppleScript dictionary, so it is macOS only; on another OS startup is refused naming the key.
It opens each viewer as a tab attached to tmux in the window whose tab carries the viewer title, else in a new window.
`none` is the only way to open no window: it opens no viewer, logs the attach command, and needs no terminal binary.
A `kitty` or `ghostty` driver SHALL always open its viewer. `orchestrator.openTerminalWindow` is retired:
startup is refused naming `terminal: none`.

#### Scenario: ghostty opens a tab in the window carrying the title
- **WHEN** `orchestrator.terminal` is `ghostty` and a viewer opens
- **THEN** one AppleScript adds a tab running `tmux attach` titled with the viewer title, to the window already holding such a tab

#### Scenario: ghostty on Linux is refused at startup
- **WHEN** `orchestrator.terminal` is `ghostty` on a Linux host
- **THEN** startup is refused with a problem on `orchestrator.terminal` telling the human to set `kitty`

#### Scenario: a reveal finds no window
- **WHEN** no Ghostty window holds a tab with the viewer title
- **THEN** the reveal answers `NOT_RUNNING` and Ghostty is not launched

#### Scenario: focus with no terminal
- **WHEN** `orchestrator.terminal` is `none` and the human presses focus
- **THEN** the board answers with a sentence naming `terminal` as the key that enables it

#### Scenario: the retired window key
- **WHEN** `orchestrator.openTerminalWindow` is set
- **THEN** jagt does not start and the problem names `terminal: none`

#### Scenario: no terminal on Linux with no kitty
- **WHEN** `orchestrator.terminal` is `none` on a Linux host with no kitty installed
- **THEN** jagt starts, no kitty is checked for, and a viewer open logs the attach command
