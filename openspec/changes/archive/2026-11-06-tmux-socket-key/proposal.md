## Why

jagt's tmux server could not be told apart from the human's own, and the options it set with `-g` leaked into the
human's sessions.

## What Changes

- `orchestrator.tmuxSocket` names a tmux server: every tmux call jagt makes and every attach it hands a viewer
  carries `-L <socket>`. Empty keeps the default server.
- The window bar, titles and status style are set on jagt's session alone: session options with `-t <session>`,
  window options by an `after-new-window` hook of that session.

## Capabilities

### Modified Capabilities

- `runtime`: jagt's tmux calls can name their own server.
