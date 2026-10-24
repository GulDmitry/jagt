## Why

On a stand run 9 sessions sat on Claude's trust dialog for ten minutes, until the watchdog ended them as "no sign of
life": the dialog opens before any hook loads, so nothing reported it.

## What Changes

- At `NEW`, `LaunchExits` reads a running session's window; a phrase the runtime declares for its trust dialog stops
  it as waiting, cause "waiting on the trust dialog", within seconds.
- jagt reads the window only; it never answers the dialog.

## Capabilities

### New Capabilities

### Modified Capabilities

- `attention`: a session waiting on the trust dialog is an ending with its cause.
