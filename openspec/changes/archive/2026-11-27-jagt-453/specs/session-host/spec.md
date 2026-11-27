## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: A renamed key reads as its old name until a release drops it
`sessionHost`, `tmuxCommand`, `tmuxSocket` and `viewer.tmuxSession` SHALL read as `multiplexer`, `tmux.command`, `tmux.socket` and
`viewer.session` until release 0.32.0 drops them; the new key wins where both are written.
Startup SHALL warn once per old key written, naming its new key and that release.
#### Scenario: An old key is written
- **WHEN** `jagt.yml` still sets `sessionHost`, `tmuxCommand`, `tmuxSocket` or `viewer.tmuxSession`
- **THEN** it takes effect as the new key and startup names each new key once
#### Scenario: Both spellings are written
- **WHEN** `jagt.yml` sets `viewer.tmuxSession: old` and `viewer.session: new`
- **THEN** the session is `new`
