## Why

A fresh session's first read took the old transcript's modification time for its last sign and called the session QUIET before any hook landed.

## What Changes

- The record a fresh session starts over with is stamped at the first read after the start, which counts as the session's last sign until a hook lands.

## Capabilities

### Modified Capabilities

- `cold-prompts`: a fresh session is not QUIET from the old transcript.
