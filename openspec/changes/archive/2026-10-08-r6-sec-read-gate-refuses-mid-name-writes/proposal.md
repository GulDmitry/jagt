## Why

A write verb mid-name was denied only by the CLI's `--disallowedTools`: `mcp__x__get_and_delete_issue` passed
`ReadGate` through the `get*` read glob.

## What Changes

- `ReadGate` refuses a tool matching a write glob when only a glob allowed it; a tool named in full still passes.

## Capabilities

### Modified Capabilities

- `protocol`: the read gate holds the write verbs itself.
