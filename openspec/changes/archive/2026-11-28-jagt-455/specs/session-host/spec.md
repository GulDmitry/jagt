## MODIFIED Requirements

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
