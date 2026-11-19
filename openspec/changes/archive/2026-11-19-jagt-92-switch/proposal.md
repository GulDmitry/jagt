## Why
A kept release is useless until `current` names it; the switch re-verifies the jar first.

## What Changes
- A kept release stores its verified sha256 beside the jar.
- A switch re-verifies the jar against it, then points `current` at the version.
