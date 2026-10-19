## Why

A turn the CLI ends on an API error other than a usage limit reaches jagt as nothing, and a session cleared or resumed is
reported as ended. A usage-limit stop shows only after the silence threshold.

## What Changes

- Every turn the CLI ends on an API error reports through one hook event, and the board names the CLI's own error word at once.
- Only the error declared as a usage limit arms the wake.
- A session ended by a reason a new start follows at once is no stop.

## Capabilities

### Modified Capabilities

- `attention`: a failed turn is a verdict at once, and a cleared or resumed session is no stop.
- `usage-limits`: every turn failure is reported; the usage-limit one arms the wake.
