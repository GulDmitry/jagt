## Why
Installing jagt is clone-and-build; a user wants one line that puts a verified, self-contained jagt in their own bin.

## What Changes
- `install.sh` (macOS, Linux) installs a release jar and a Temurin 25 JRE per user, no sudo, each verified against its published sha256.
- Later steps: the release publishes the installers, `install.ps1`, the hourly check with `jagt upgrade` and the board's verb, the site's install section.
