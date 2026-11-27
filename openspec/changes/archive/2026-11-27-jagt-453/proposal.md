## Why

The key choosing the terminal multiplexer was named `sessionHost`, and the tmux settings and the session name carried the
vendor's name at the top level. A vendor's name belongs only on that vendor's own block.

## What Changes

- `orchestrator.multiplexer` (`tmux` or `herdr`) chooses the tool; a vendor's name is only its value.
- `viewer.session` names the session either tool runs.
- Each tool's settings sit in its own block: `tmux: {command, socket}`, `herdr: {command, home}`.
- The old keys (`sessionHost`, `tmuxCommand`, `tmuxSocket`, `viewer.tmuxSession`) read as aliases until release 0.32.0;
  startup names the new key of each one written, once.
- A fitness row fails on a vendor's name in a config key outside that vendor's own block.

## Capabilities

### New Capabilities

### Modified Capabilities

- `session-host`: the key, the blocks, the session name and the aliases.
- `runtime`: the tmux socket's key.
- `startup`: the viewer session's key.
- `session-stream`: the herdr stream's key.
