## Why

A push connection a sleeping laptop dropped stays open to the page, so the board showed `live` while stale.

## What Changes

- `TaskEventStream` sends a `beat` every 20s; a page missing two of them reconnects, `backend unreachable` meanwhile.

## Capabilities

### Modified Capabilities

- `board`: a silent connection counts as dropped.
