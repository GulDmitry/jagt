## Why

`BoardWriteFilter` admitted an `/api` write carrying the Master token, which `OriginFilter` then booked as the
human's; nothing sends the token there ([0026](../../../../docs/decisions/0026-the-guards-gaps-after-round-three.md)).

## What Changes

- A write under `/api`, hooks' `/api/agent/` aside, carries the page's Origin, else 403. The Master acts through `/mcp`.

## Capabilities

### Modified Capabilities

- `board`: a write is the page's.
