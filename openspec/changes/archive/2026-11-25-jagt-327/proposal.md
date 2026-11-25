## Why

herdr is a terminal multiplexer built for coding agents, with persistent named sessions and a socket API. It is offered as a
session host beside tmux, which stays the default.

## What Changes

- `orchestrator.session-host` (`tmux` default, `herdr`) chooses the `SessionHost`, its `WindowStream` and `ReadScope`.
- A herdr window is a tab labelled with its name; the runtime guarantees of a tmux window hold for it.
- Startup names `orchestrator.herdr.command` where herdr is chosen and not installed.

## Capabilities

### New Capabilities

- `session-host`: the key choosing the session host, and what a herdr window keeps of a tmux window.

### Modified Capabilities

- `runtime`: the session host is tmux by default, herdr by key.
