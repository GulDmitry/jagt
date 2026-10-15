## Why

A task session could write anywhere the agent CLI's own rules let it: an `rm -rf` outside the worktree, a system file.

## What Changes

- A session's shell writes only inside its task, under the agent CLI's own OS sandbox; the network stays open.
- `orchestrator.agent.sandbox` adds paths and commands, or turns the sandbox off.

## Capabilities

### Modified Capabilities

- `guards`: a session writes only inside its task.
