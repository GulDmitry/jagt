## Why

A timed-out command lost only its own pid: a headless agent's MCP and tool children outlived it. An interrupted wait and
a stopping jagt left the command running.

## What Changes

- A command jagt waits on is killed with its descendants on a timeout, on an interrupt and when jagt stops.

## Capabilities

### Modified Capabilities

- `runtime`: a waited-on command leaves no process behind.
