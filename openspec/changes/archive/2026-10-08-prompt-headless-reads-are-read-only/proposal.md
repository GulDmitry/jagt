## Why

The Master's reviewer read an untrusted diff with every shell command approved, and the headless reads took ticket
text with every built-in tool loaded: a line in either could push, clean the tree or write to the host.

## What Changes

- The reviewer runs in `dontAsk` with read-only git, Read, Grep and Glob; `assistant.permission-mode` no longer reaches it.
- A headless read loads no built-in tool; with `allowed-tools` set it runs in `dontAsk`.
- Both deny every MCP tool whose name starts with a write verb: MCP tools can be allowed by server or name only.

## Capabilities

### Modified Capabilities

- `master`: the reviewer may only read.
- `protocol`: a ticket read loads no tool that writes.
