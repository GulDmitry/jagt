## Why

The board spec says no command gets its own endpoint, yet the launch row posts to `POST /api/tasks`: its pickers carry
fields the typed grammar of `do` cannot.

## What Changes

- The board spec names the launch row's structured `POST /api/tasks` as the one exception.

## Capabilities

### Modified Capabilities

- `board`: the launch row's endpoint is named.
