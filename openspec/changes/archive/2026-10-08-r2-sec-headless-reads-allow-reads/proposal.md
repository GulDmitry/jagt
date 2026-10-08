## Why

A deny-list of write verbs missed every tool not named after one (`python_repl`, a browser's `evaluate_script`), and
the reads ran `bypassPermissions` beside it; the reviewer allowed every server whole.

## What Changes

- A headless read and the Master's reviewer run `dontAsk` and may call only an MCP read: `get*`, `list*`,
  `search*`, `read*`, `fetch*`, `query*`, `find*`, `describe*`, `lookup*` of each server they load.
- `assistant.allowed-tools` widens that list; `assistant.permission-mode` is gone.
- The write-verb deny-list stays, for a server the human's own settings allow whole.

## Capabilities

### Modified Capabilities

- `protocol`: a ticket read calls only MCP reads.
