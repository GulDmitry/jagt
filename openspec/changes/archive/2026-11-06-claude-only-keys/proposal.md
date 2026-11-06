## Why

`agent.outputStyle` and `agentDisabledPlugins` configure only Claude Code but sat under agent-neutral names.

## What Changes

- **BREAKING** `agent.outputStyle` is `claude.outputStyle`; `agentDisabledPlugins` is `claude.disabledPlugins`, now read live.
- A `jagt.yml` still carrying an old key refuses the start, naming the old key and the new.

## Capabilities

### Modified Capabilities

- `startup`: a moved key is refused with its new place.
- `runtime`: the plugins a worktree disables come from `claude.disabledPlugins`.
