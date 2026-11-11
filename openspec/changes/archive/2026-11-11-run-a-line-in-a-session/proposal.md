## Why

A command the session cannot run itself is run from the board: the human types the CLI's shell escape into the say field, and a cold
session turns that line into words relayed under the brief.

## What Changes

- A `run` verb on the Session tab types the line behind the CLI's shell escape, warm or cold, never as words.
- `AgentRuntime` gives each driver its shell-escape syntax; a driver with none offers no `run`.
- The log keeps that a line ran, never the line.

## Capabilities

### Modified Capabilities

- `board-verbs`: the Session tab's `run` verb.
