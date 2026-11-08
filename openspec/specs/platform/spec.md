# platform Specification

## Purpose

What jagt asks of the machine it runs on, each only when `jagt.yml` asks for it, and what it never takes from the machine: its locale and zone.

## Requirements

### Requirement: The machine stays awake only when asked
With `keepAwake: true`, the `keepawake` job SHALL hold `port/KeepAwake` while the Master is in `act`, auto-review
is enabled or `tracker.mode` takes work. Each minute it SHALL check, releasing the hold otherwise. On macOS it is `caffeinate -i -w
<jagt's pid>`, on Linux `systemd-inhibit --what=idle:sleep` around `tail --pid=<jagt's pid>`, each ending with jagt.
Unset, no job is declared and nothing is spawned.

#### Scenario: Unattended work on
- **WHEN** `keepAwake: true` and `roles.master.mode: act`
- **THEN** the machine does not idle-sleep until the mode changes or jagt stops

#### Scenario: Not asked
- **WHEN** `keepAwake` is unset
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
`orchestrator.terminal` SHALL select the `TerminalDriver`: `kitty` (default) or `ghostty`. `GhosttyTerminalDriver` SHALL
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

### Requirement: The editor is a launcher
jagt SHALL open a worktree and a diff only through `editorCommand` and the optional `editorDiffCommand`, naming no editor.
IDE service folders present in the base repository SHALL be copied into a fresh worktree. A file the worktree already holds SHALL stay as it is.

#### Scenario: IDE service folders, or no diff command
- **WHEN** the base repository holds `.idea/runConfigurations` or `.vscode/launch.json`
- **THEN** a fresh worktree holds the same run configurations or file
- **WHEN** the base repository holds no IDE service folder
- **THEN** nothing is copied
- **WHEN** `editorDiffCommand` is unset and the human presses diff
- **THEN** it is refused, naming the key and an example; jagt still starts

### Requirement: jagt stops a worktree's container stacks
Removing a worktree SHALL first take down every Compose project whose compose file lies under it
(`port/WorktreeContainers`, `DockerComposeContainers.stop`, `docker compose -p <project> down -v --remove-orphans`). A project outside the
worktree SHALL never be touched. `orchestrator.containers` selects the driver: `docker-compose` (default) or `none`.
A missing or failing Docker SHALL log at most a WARN and never stop the removal.

#### Scenario: A task is done with a stack running in its worktree
- **WHEN** ABC-42's worktree is removed and project `alpha`'s compose file lies in it
- **THEN** `alpha` is taken down with its volumes before the directory is deleted

#### Scenario: Another project is running
- **WHEN** project `beta`'s compose file lies outside the worktree
- **THEN** `beta` is left running

#### Scenario: Docker is not installed
- **WHEN** the worktree is removed on a machine without Docker
- **THEN** the worktree is removed and nothing is logged above WARN
