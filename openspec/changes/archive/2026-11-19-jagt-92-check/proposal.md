## Why
A running jagt cannot tell that a newer release exists, so every upgrade is a manual reinstall.

## What Changes
- An hourly job reads the release feed and keeps a newer release, checksum-verified, under `versions/` beside the running one.
- `current` is not touched and nothing restarts.
