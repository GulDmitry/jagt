## Why
Nothing proves that the installers, the launcher and the upgrade work together on the three systems they target.

## What Changes
- CI runs an install check on Linux, macOS and Windows: install, start, keep a newer release, upgrade, restart.
- Every release attaches `install.ps1` beside `install.sh`.
- The installers derive the runtime's checksum URL from the feed's first redirect, not the last: the last one is a signed storage URL.
- The macOS and Linux launcher starts the jar by its version folder: through the `current` link a running jagt found no layout and kept no release.
