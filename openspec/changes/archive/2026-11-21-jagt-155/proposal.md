## Why

`jagt.yml` is read on every access, so a live edit to a number outside its range reached the runtime unchecked: the range was judged at start only.

## What Changes

- A live read keeps the last valid value of a number outside its range, the default before any.
- The board's head names key, value and range until a read finds the number valid.
- `autoReview.maxIntervalMinutes` and `autoReview.waitingMaxIntervalMinutes` below their floor are refused, no longer raised in silence.

## Impact

- `openspec/specs/project-setup/spec.md`
