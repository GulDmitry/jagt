## Why

A ship-count stop on a request outlived that request: the session opened a follow-up, and auto-review refused it as stopped.

## What Changes

- A stop counted on a request records it; a new request replacing it lifts the stop and restarts the count.

## Capabilities

### Modified Capabilities

- `unattended-work`: the stopped-task requirement names the new request as a second way a stop clears.

### Added Requirements

- `task-flow`: a follow-up request after a merged one.
