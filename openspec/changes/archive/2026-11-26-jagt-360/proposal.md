## Why

Four specs name a site or wording the code has outgrown: a push site, the no-reset wake case, the startup Master checks and the Herdr stream.

## What Changes

- `git`: `WorkspaceBranch` is the third push site.
- `usage-limits`: no wake is armed when any full window has no reset time.
- `startup`: the Master's start checks are `MasterMineCheck` (the words of `mine`) and `MasterCheck` (the rest).
- `session-stream`: Herdr is served by a redrawn view that never resizes the window.
