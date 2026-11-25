## Why

A session handed back a test that passed only after it reset the test database by hand; the rerun failed on the stale
state and the task stopped on an unchanged failure.

## What Changes

- The session's standing rules require a verification command to set up and reset its own state and to pass twice in a row before REVIEW_PENDING.

## Capabilities

### Modified Capabilities

- `session-brief`: the standing rules carry the clean-rerun rule.
