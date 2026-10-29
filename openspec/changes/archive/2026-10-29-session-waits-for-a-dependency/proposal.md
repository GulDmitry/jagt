## Why

A session whose database was down asked a human about it, though nobody could bring it up and it would be back on its own.

## What Changes

- A session reports IN_PROGRESS with `outcome=wait`, naming what is down and how to probe it.
- jagt says `wait-over` after each wait of `infraWait.backoff`, and stops the task as `WAIT` after the last.
- A wake belongs to the turn it was armed in.

## Capabilities

### Modified Capabilities

- `infra-wait`: a session waits out a dependency that is down.
