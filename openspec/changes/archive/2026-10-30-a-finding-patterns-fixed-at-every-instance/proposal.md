## Why

A finding names its pattern, yet the session was asked to fix only the cited line, so the next round could cite the same
class elsewhere in the diff.

## What Changes

- The session fixes every instance its diff holds of a finding's pattern, not only the one cited.

## Capabilities

### Modified Capabilities

- `master`: a finding's pattern is fixed at every instance the diff holds.
