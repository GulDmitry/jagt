## Why

A failed `tmux list-windows` read as "no such window", so the 10s master feed job opened a second `master` window.

## What Changes

- A session whose windows cannot be listed opens no window and logs why; a window that exists is never duplicated.

## Capabilities

### Modified Capabilities

- `runtime`: the one-name-one-window requirement gains a scenario.
