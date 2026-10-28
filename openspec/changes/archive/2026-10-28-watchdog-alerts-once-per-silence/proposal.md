## Why

One 35-minute silence sent seven "agent stopped" alerts, one per stale window, while the card already showed it.

## What Changes

- The watchdog alerts once per silence, keyed on its start; a new silence alerts again.

## Capabilities

### Modified Capabilities

- `attention`: the watchdog requirement gains a scenario.
