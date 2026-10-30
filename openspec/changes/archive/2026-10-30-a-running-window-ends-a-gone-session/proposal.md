## Why

A session ended by a window read or a hook stayed gone after its agent process ran again.

## What Changes

- A read finding the window running its agent ends a gone session; a failure stays.
- A status move keeps an idle halt and ends a quiet one.

## Capabilities

### Modified Capabilities

- `attention`: a running window ends a gone session.
