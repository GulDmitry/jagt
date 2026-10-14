## Why

In `act` the Master answered the question its own review asked, and a `do` line in that answer opened a task: work
it found unasked could amplify itself into new tasks with no human in between.

## What Changes

- An answer to the reviewer's own question opens no task; the session is told it was not opened.
- An answer to the session's question still opens what its `do` line names.

## Capabilities

### Modified Capabilities

- `master`: a `do` line opens nothing when it answers the reviewer's own question.
