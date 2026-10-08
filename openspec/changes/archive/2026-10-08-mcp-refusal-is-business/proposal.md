## Why

A `flow/Refusal` and "Task X not found" both extend `IllegalArgumentException`, so a session was told `validation`:
fix the fields and resend a call that can never pass.

## What Changes

- A `flow/Refusal` is `business`; a report on a task gone from `state.json` throws one (`NO_SUCH_TASK`).

## Capabilities

### Modified Capabilities

- `protocol`: which failure category a refusal carries.
