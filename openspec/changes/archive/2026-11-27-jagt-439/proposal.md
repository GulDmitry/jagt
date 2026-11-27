## Why

The data folder defaulted to `<root>/data`, so a task worktree lay inside jagt's own checkout and the agent CLI loaded that checkout's
`AGENTS.md`, local rules and hooks as the managed project's own.

## What Changes

- The default data folder is `~/.jagt/data`; an existing `<root>/data` with a state stays and is named once at startup.
- Startup notes a data folder inside a git checkout holding `AGENTS.md` or `CLAUDE.md`, naming the path and `orchestrator.data`.

## Capabilities

### Modified Capabilities

- `startup`: the data folder requirement gains its new default and two scenarios.
