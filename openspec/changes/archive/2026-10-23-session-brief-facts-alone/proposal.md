## Why

A step left empty in the session brief told the session nothing, so emptying `plan` would leave an approved plan's
session waiting forever.

## What Changes

- A step left empty sends jagt's facts of that step alone.
- The plan's go is the brief's `plan` step.

## Capabilities

### New Capabilities

### Modified Capabilities

- `session-brief`: an empty step sends its facts alone.
