## Why

`ToolGate` read any word `gh` or `glab` as a host-CLI call: `grep -rn glab src`, `which gh` and
`git commit -m "bump gh to 2.0"` were refused.

## What Changes

- Only a `gh` or `glab` in command position is judged: the first word after `VAR=` prefixes and the `env`,
  `command`, `exec` and `xargs` wrappers. A quoted span is one word of data.

## Capabilities

### Modified Capabilities

- `git`: the host CLIs are judged in command position.
