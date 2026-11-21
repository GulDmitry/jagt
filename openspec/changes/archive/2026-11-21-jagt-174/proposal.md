## Why

Killing the installed launcher's PID left its JVM serving the port: the launcher ran java as a plain child and ended on the signal.

## What Changes

- The macOS and Linux launcher passes TERM and INT to its jar, waits for it, then ends.
- `docs/installation.md` says how to stop jagt.

## Impact

- `openspec/specs/release-artifacts/spec.md`
