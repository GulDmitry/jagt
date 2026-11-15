## Why

A redeploy stops the process; its sessions died unrecorded, so the next start read them as running until a window read said otherwise.

## What Changes

- Closing the context ends every live session as gone, closes its windows and leaves `state.json` holding that.
- Requests in flight finish first (`server.shutdown: graceful`, 20 s) so jagt exits within 30 s.
- A new spec `shutdown`: the runtime spec is at its page budget.

## Capabilities

### New Capabilities

- `shutdown`: stopping jagt records each live session as gone before its windows close.
