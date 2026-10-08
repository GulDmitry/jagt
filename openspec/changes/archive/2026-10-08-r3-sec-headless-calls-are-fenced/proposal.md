## Why

A headless read inherited every allow rule of the human's own settings: the Master's reviewer could run
`awk 'BEGIN{system(…)}'`, `tmux send-keys` or `curl` from a diff, and a ticket read any tool of a server allowed
whole ([0027](../../../../docs/decisions/0027-a-headless-call-is-fenced-by-jagt.md)).

## What Changes

- Every headless run puts each call to jagt first (`ReadGate`): named tools, file tools inside the round's
  worktrees, one read-only git command. Unreachable, jagt refuses every call.
- The verb deny-list stays as a second line and gains the verbs it missed, mid-name too.

## Capabilities

### Modified Capabilities

- `master`: the reviewer is held to its round's worktrees and read-only git, whatever the human allows.
- `protocol`: a read calls only its MCP reads and what `allowed-tools` adds, whatever the human allows.
