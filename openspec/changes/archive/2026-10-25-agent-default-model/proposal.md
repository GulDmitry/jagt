## Why

A fresh session without a `next-model:` line ran the CLI default, and no key chose a default for a whole install.

## What Changes

- `orchestrator.agent.model`, blank by default, is the model a session starts and is revived on when the notes name none.
- A name outside the runtime's `models()` refuses startup; a runtime that cannot choose ignores the key.

## Capabilities

### Modified Capabilities

- `runtime`: a fresh start takes the configured model where the notes name none.
