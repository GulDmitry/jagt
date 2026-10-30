## Why

A deploy that handed back for a red pipeline, or whose check was asked, was told apart by the task's text and the turn's mark; the
10-minute wait for a pipeline counted from the status, which a hand-back restarts.

## What Changes

- The 10 minutes count from the push recorded for the repository; a deploy begun before the record counts from its status.
- A red pipeline hand-back flags the target conflicted in the record, with its cause; the check is recorded as asked there.

## Capabilities

### Modified Capabilities

- `infra-wait`: the clock of the no-pipeline wait.
