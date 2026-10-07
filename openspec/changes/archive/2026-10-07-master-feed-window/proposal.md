## Why

The Master's steps showed only as their effects: a session's window vanished, another opened, and the reason sat in
jagt's machine log ([0017](../../../docs/decisions/0017-the-master-is-seen-in-one-feed-window.md)).

## What Changes

- One `master` window in jagt's tmux session follows every Master step, for every project, and comes back if closed.

## Capabilities

### Modified Capabilities

- `master`: its steps are followed in one window.
