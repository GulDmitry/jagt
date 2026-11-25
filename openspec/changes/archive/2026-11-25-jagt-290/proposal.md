## Why

A protocol field padded with blanks read differently by message, and a hook's unparseable log path threw.

## What Changes

- Every protocol message reads a blank field as absent and strips the rest, through one rule.
- A hook's transcript path the file system cannot parse is dropped as a violation.

## Capabilities

### New Capabilities

### Modified Capabilities

- `protocol`: one blank rule for every message; an unparseable log path is filtered.
