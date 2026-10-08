## Why

`ToolGate` matched the board's port on four loopback names; `127.1`, `2130706433`, `0x7f000001`, `LOCALHOST` and
`[::ffff:127.0.0.1]` reach the same socket.

## What Changes

- A line naming the board's port is refused whatever host precedes it, and the token's name in any case.

## Capabilities

### Modified Capabilities

- `git`: `ToolGate` refuses the board's port on any host.
