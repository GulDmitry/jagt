## Why

Only Claude listed its models and Codex was never told one, so a Codex session could not run on a chosen model.

## What Changes

- `orchestrator.agent.models` names the models a session may be told, replacing the agent CLI's own list.
- A Codex session launches with `-m <model>` when one is chosen.

## Capabilities

### Modified Capabilities

- `runtime`: the allowed models come from config before the agent CLI.
