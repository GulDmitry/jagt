## Why
The install line needs a URL that always serves the newest `install.sh`.

## What Changes
- The release job attaches `install.sh` under that unversioned name, so `releases/latest/download/install.sh` serves it.
