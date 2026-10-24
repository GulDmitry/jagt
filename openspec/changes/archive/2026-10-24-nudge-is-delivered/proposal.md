## Why

A nudge typed as the session's turn ended stayed in its input, never sent; the session idled until the watchdog.
It also said "The Master updated task_context.md" for steps jagt relays itself.

## What Changes

- A nudge left unsent in the input is submitted again, three times at most; then the relay says it failed and why.
- The nudge names no author: "task_context.md changed".

## Capabilities

### New Capabilities

### Modified Capabilities

- `master`: a nudge is delivered or its failure is named.
