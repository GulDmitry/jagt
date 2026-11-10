## Why

The board's push named `StateService.onChange`, a listener list that no longer exists; a written state is now one `StateWritten` event.

## What Changes

- `TaskEventStream` forwards the `StateWritten` event, not a `StateService` callback.

## Capabilities

### Modified Capabilities

- `board`: the push names the event it forwards.
