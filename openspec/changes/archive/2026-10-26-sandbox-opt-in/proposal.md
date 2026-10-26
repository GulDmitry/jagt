## Why

Claude's sandbox lets the network out only through its local proxy, which a JVM does not read:
a session's Gradle fails with `Operation not permitted`.

## What Changes

- `orchestrator.agent.sandbox.enabled` defaults to `false`; a session's write boundary applies when it is `true`.

## Capabilities

### New Capabilities

### Modified Capabilities

- `guards`: the session sandbox is opt-in.
