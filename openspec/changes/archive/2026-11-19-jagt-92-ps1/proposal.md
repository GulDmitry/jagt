## Why
Windows users have no installer: `install.sh` runs on macOS and Linux only.

## What Changes
- `install.ps1` installs a release jar, a Temurin 25 JRE and a `jagt.cmd` launcher per user on Windows, verified like `install.sh`.
- The fixed-URL sentence becomes its own requirement, since the first grew past the 500-character limit.
