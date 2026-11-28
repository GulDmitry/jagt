# session-host Specification

## Purpose
Which session host keeps the agents running, chosen by a key, and what a herdr window keeps of a tmux window.

## Requirements

### Requirement: The session host is chosen by a key, tmux the default
`orchestrator.multiplexer` SHALL be `tmux` or `herdr`, unset `tmux`; a vendor's name is a value, never part of a key outside its own block.
The tool not chosen SHALL have no beans: `SessionHost`, `WindowStream`, `ReadScope` and its startup check.
A chosen herdr SHALL be one the startup check finds, naming `orchestrator.herdr.command`.
`viewer.session` SHALL name the session either tool runs; settings sit in `tmux: {command, socket}` and `herdr: {command, home}`.

#### Scenario: The key is unset, herdr, or herdr is not installed
- **WHEN** `multiplexer` is unset - **THEN** every task runs in a tmux window as before
- **WHEN** `multiplexer` is `herdr` and a task starts - **THEN** its session runs in a herdr tab; Focus selects it and raises the viewer
- **WHEN** `multiplexer` is `herdr` and the binary is on neither PATH nor the usual install directories
- **THEN** startup is refused naming `orchestrator.herdr.command` and the binary

#### Scenario: The session has a name
- **WHEN** `jagt.yml` sets `multiplexer: herdr` and `viewer.session: agents`
- **THEN** herdr runs every session under the name `agents`

### Requirement: A herdr window is a tab named for its task
A herdr window SHALL be a tab labelled with its name, in the workspace labelled with the session. Opening one SHALL first close any same-named tab.
The agent's line SHALL replace the tab's shell and end in a 15s tail, after which the tab closes with no shell left. A revived window SHALL
re-enter (`AgentRuntime.reviveCommand`).

#### Scenario: A herdr window opens, exits or is revived
- **WHEN** a task opens a window whose name a tab already holds - **THEN** that tab is closed and one tab holds the name
- **WHEN** the agent exits - **THEN** the tab shows the tail for 15s and closes, and until then reads as a dead shell
- **WHEN** the server was lost and a session is revived - **THEN** the revived session re-enters with its revive command

### Requirement: A herdr read decides only what it read
Liveness SHALL be a foreground process other than the pane's own. No herdr socket, or a refused connection, SHALL list no window; any other
failed read SHALL be unknown, deciding nothing as with tmux. A job run SHALL list the session once for all its tasks (`ReadScope`). A window's
last activity SHALL be the time jagt last saw its screen change, which herdr does not report.

#### Scenario: A herdr window cannot be listed or its screen is read again
- **WHEN** herdr cannot list the session's tabs for any cause but an absent server
- **THEN** jagt opens and closes no tab, and logs `herdr windows unreadable` with the cause
- **WHEN** a screen is first read, then read again unchanged, then changed
- **THEN** its activity is the first read's time, unchanged, then the changing read's time

### Requirement: A renamed key reads as its old name until a release drops it
`sessionHost`, `tmuxCommand`, `tmuxSocket`, `viewer.tmuxSession`, `kittyCommand` and `kittyFontSize` SHALL read as `multiplexer`,
`tmux.command`, `tmux.socket`, `viewer.session`, `kitty.command` and `kitty.fontSize` until release 0.32.0 drops them;
the new key wins where both are written.
Startup SHALL warn once per old key written, naming its new key and that release.
#### Scenario: An old key is written
- **WHEN** `jagt.yml` still sets `sessionHost`, `tmuxCommand`, `tmuxSocket`, `viewer.tmuxSession`, `kittyCommand` or `kittyFontSize`
- **THEN** it takes effect as the new key and startup names each new key once
#### Scenario: Both spellings are written
- **WHEN** `jagt.yml` sets `viewer.tmuxSession: old` and `viewer.session: new`
- **THEN** the session is `new`
