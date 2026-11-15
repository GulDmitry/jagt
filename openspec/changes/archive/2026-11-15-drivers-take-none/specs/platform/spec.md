## MODIFIED Requirements

### Requirement: The machine stays awake only when asked
With `keepAwake: true`, the `keepawake` job SHALL hold `port/host/KeepAwake` while the Master is in `act`, auto-review
is enabled or `tracker.mode` takes work. Each minute it SHALL check, releasing the hold otherwise. On macOS it is `caffeinate -i -w
<jagt's pid>`, on Linux `systemd-inhibit --what=idle:sleep` around `tail --pid=<jagt's pid>`, each ending with jagt.
Unset or `none`, no driver and no job is declared and nothing is spawned.

#### Scenario: Unattended work on
- **WHEN** `keepAwake: true` and `roles.master.mode: act`
- **THEN** the machine does not idle-sleep until the mode changes or jagt stops

#### Scenario: Not asked
- **WHEN** `keepAwake` is unset or `none`
- **THEN** nothing is spawned, whatever runs unattended

### Requirement: The viewer driver is chosen by orchestrator.terminal
`orchestrator.terminal` SHALL select the `TerminalDriver`: `kitty` (default), `ghostty` or `none`. `GhosttyTerminalDriver` SHALL
drive Ghostty through its AppleScript dictionary, so it is macOS only; on another OS startup is refused naming the key.
It opens each viewer as a tab attached to tmux in the window whose tab carries the viewer title, else in a new window.
`none` opens no viewer and needs no terminal binary.

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

### Requirement: The editor is a launcher
jagt SHALL open a worktree and a diff only through `editorCommand` and the optional `editorDiffCommand`, naming no editor.
`orchestrator.editor` SHALL be `cli` (default) or `none`; `none` opens nothing.
IDE service folders present in the base repository SHALL be copied into a fresh worktree. A file the worktree already holds SHALL stay as it is.

#### Scenario: IDE service folders, or no diff command
- **WHEN** the base repository holds `.idea/runConfigurations` or `.vscode/launch.json`
- **THEN** a fresh worktree holds the same run configurations or file
- **WHEN** the base repository holds no IDE service folder
- **THEN** nothing is copied
- **WHEN** `editorDiffCommand` is unset and the human presses diff
- **THEN** it is refused, naming the key and an example; jagt still starts

#### Scenario: open with no editor
- **WHEN** `orchestrator.editor` is `none` and the human presses open or diff
- **THEN** it is refused with a sentence naming `editor` as the key that enables it; jagt still starts

## ADDED Requirements

### Requirement: The notifier is chosen by orchestrator.notifier
`orchestrator.notifier` SHALL select the `UserNotifier`: `desktop` (default, the platform's own notifier) or `none`.
`none` shows no banner and logs each skipped one; startup SHALL say once, naming `notifier`, that banners are off.

#### Scenario: banners with no notifier
- **WHEN** `orchestrator.notifier` is `none`
- **THEN** startup notes that no banner is shown and names `notifier` as the key enabling them, and no notifier binary is needed

#### Scenario: A machine with no display
- **WHEN** `terminal`, `notifier`, `keepAwake` and `editor` are `none` on a machine with no kitty and no display
- **THEN** jagt starts and the board answers
