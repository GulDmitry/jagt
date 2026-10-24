## Why

A report's meaning was folded into the task's line as a marker and parsed back out at every reader, so a word in the
line could change whose move it is (decision 0072).

## What Changes

- The task carries its report as `task/AgentReport` (question, permission, no changes, plain); the line is the human's
  words alone, and no reader parses it.
- An outcome typed into the message with no `outcome` field is words, not a report.
- A `state.json` written before the field reads it once from the line's marker, which leaves the line.

## Capabilities

### New Capabilities

### Modified Capabilities

- `review`: the outcome is a field on the task, never a marker.
- `protocol`: a permission is the task's report, not words in its line.
