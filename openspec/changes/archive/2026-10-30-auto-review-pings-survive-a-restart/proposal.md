## Why

A restart emptied the rounds auto-review had told you about, so each elapsed window and unreadable round was told again.

## What Changes

- The round each of the two pings was last sent for is kept on the task in `state.json`, and cleared where the poller drops the task.

## Capabilities

### Modified Capabilities

- `unattended-work`: an elapsed polling window is told once across restarts.
