# platform Specification

## Purpose

What jagt asks of the machine it runs on, each only when `jagt.yml` asks for it, and what it never takes from the machine: its locale and zone.

## Requirements

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

### Requirement: jagt shows every time in UTC and every text in English
`main` SHALL set the default locale to English and the default zone to UTC before anything else. The board SHALL format
every time in UTC and English, and mark UTC where a time is shown in full. Nothing jagt shows or writes SHALL depend on
the machine's or the browser's locale or zone.

#### Scenario: A board in Berlin
- **WHEN** a task changed at 14:05 UTC and the board is open in a browser set to Berlin
- **THEN** its history reads 14:05

#### Scenario: A machine in Turkish
- **WHEN** jagt starts on a machine whose language is Turkish
- **THEN** every case change and every date it renders is the English one

### Requirement: The viewer driver is chosen by orchestrator.terminal
`orchestrator.terminal` SHALL select the `TerminalDriver`: `kitty` (default), `ghostty` or `none`. `GhosttyTerminalDriver` SHALL
drive Ghostty through its AppleScript dictionary, so it is macOS only; on another OS startup is refused naming the key.
It opens each viewer as a tab attached to tmux in the window whose tab carries the viewer title, else in a new window.

#### Scenario: ghostty opens a tab in the window carrying the title
- **WHEN** `orchestrator.terminal` is `ghostty` and a viewer opens
- **THEN** one AppleScript adds a tab running `tmux attach` titled with the viewer title, to the window already holding such a tab

#### Scenario: ghostty on Linux is refused at startup
- **WHEN** `orchestrator.terminal` is `ghostty` on a Linux host
- **THEN** startup is refused with a problem on `orchestrator.terminal` telling the human to set `kitty`

#### Scenario: a reveal finds no window
- **WHEN** no Ghostty window holds a tab with the viewer title
- **THEN** the reveal answers `NOT_RUNNING` and Ghostty is not launched

### Requirement: Only terminal none opens no viewer
`none` is the only way to open no window: it opens no viewer, logs the attach command, and needs no terminal binary.
A `kitty` or `ghostty` driver SHALL always open its viewer. `orchestrator.openTerminalWindow` is retired:
startup is refused naming `terminal: none`.

#### Scenario: focus with no terminal
- **WHEN** `orchestrator.terminal` is `none` and the human presses focus
- **THEN** the board answers with a sentence naming `terminal` as the key that enables it

#### Scenario: the retired window key
- **WHEN** `orchestrator.openTerminalWindow` is set
- **THEN** jagt does not start and the problem names `terminal: none`

#### Scenario: no terminal on Linux with no kitty
- **WHEN** `orchestrator.terminal` is `none` on a Linux host with no kitty installed
- **THEN** jagt starts, no kitty is checked for, and a viewer open logs the attach command

### Requirement: The editor is a launcher
jagt SHALL open a worktree and a diff only through `editorCommand` and the optional `editorDiffCommand`, naming no editor.
`orchestrator.editor` SHALL be `cli` (default) or `none`; `none` opens nothing. `editorCommand` has no default: unset, `code` opens
the board's viewer alone and startup raises no problem; set, `code` also launches it.
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

#### Scenario: no editorCommand
- **WHEN** `editorCommand` is unset and the human presses code
- **THEN** the Code tab opens, no editor launches and startup raises no problem
- **WHEN** `editorCommand` is set and the human presses code
- **THEN** the Code tab opens and the editor launches too

### Requirement: jagt stops a worktree's container stacks
A compose command SHALL run as `<repository>-<task>` of its own worktree, wherever its session started.
Removing a worktree SHALL first run `compose down -v` on that project and each whose compose file lies under it (`WorktreeContainers.stop`).
Another project SHALL never be touched. `orchestrator.containers` is `docker-compose` (default) or `none`, which names none.
A missing or failing Docker SHALL log a WARN at most and never stop the removal.

#### Scenario: A task is done with a stack running in its worktree
- **WHEN** ABC-42's worktree is removed and project `alpha`'s compose file lies in it
- **THEN** `alpha` is taken down with its volumes before the directory is deleted

#### Scenario: Another project is running
- **WHEN** project `beta`'s compose file lies outside the worktree
- **THEN** `beta` is left running

#### Scenario: Docker is not installed
- **WHEN** the worktree is removed on a machine without Docker
- **THEN** the worktree is removed and nothing is logged above WARN

#### Scenario: Two tasks work on one repository
- **WHEN** ABC-1 and ABC-2 each start a session in their worktree of `shop`
- **THEN** the sessions run as projects `shop-abc-1` and `shop-abc-2`, and closing ABC-1 takes down `shop-abc-1` alone

#### Scenario: A session runs compose in its second repository
- **WHEN** a session started in ABC-42's worktree of `a` runs a compose command in its worktree of `b`
- **THEN** the project is `b-abc-42`

### Requirement: The notifier is chosen by orchestrator.notifier
`orchestrator.notifier` SHALL select the `UserNotifier`: `desktop` (default, the platform's own notifier) or `none`.
`none` shows no banner and logs each skipped one; startup SHALL say once, naming `notifier`, that banners are off.

#### Scenario: banners with no notifier
- **WHEN** `orchestrator.notifier` is `none`
- **THEN** startup notes that no banner is shown and names `notifier` as the key enabling them, and no notifier binary is needed

#### Scenario: A machine with no display
- **WHEN** `terminal`, `notifier`, `keepAwake` and `editor` are `none` on a machine with no kitty and no display
- **THEN** jagt starts and the board answers

### Requirement: The kitty viewer's settings sit in its own block
The kitty viewer SHALL run `kitty.command`, unset `kitty`, and pass `kitty.fontSize` as its font size; unset leaves kitty's own.

#### Scenario: The block is written
- **WHEN** `jagt.yml` sets `kitty.command: /opt/kitty` and `kitty.fontSize: 15`
- **THEN** the viewer runs `/opt/kitty` at font size 15
