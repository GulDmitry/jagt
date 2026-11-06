## ADDED Requirements

### Requirement: jagt's tmux calls can name their own server
With `orchestrator.tmuxSocket` set, every tmux call jagt makes and every attach it hands a viewer SHALL carry
`-L <socket>`; empty keeps tmux's default server. The window bar, titles and mouse SHALL be set on jagt's session alone, never `-g`.
#### Scenario: A socket is named
- **WHEN** `tmuxSocket` is `jagt` and a task window opens **THEN** each tmux call starts `tmux -L jagt`; the human's sessions keep their options
