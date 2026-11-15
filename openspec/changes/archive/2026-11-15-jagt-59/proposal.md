## Why

A read-only root (an installed jar, a mounted config) refused to start: the token was written into the root.

## What Changes

- The Master's token lives in `master-token` beside `state.json`, mode 0600; the bridge reads it from there.

## Impact

- `protocol`: the Master requirement names the new path. `startup`: one requirement on where the token is written. Out of scope: auth for the board.
