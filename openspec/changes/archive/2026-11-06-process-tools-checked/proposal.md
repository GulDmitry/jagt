## Why

A missing `pgrep` read every agent's window as a dead shell; a missing `lsof` or `pkill` failed silently.

## What Changes

- `pgrep` missing refuses the start; `lsof` or `pkill` missing is said once at start.

## Capabilities

### Modified Capabilities

- `startup`: a missing tidying tool is a note, not a refusal.
