## Why

A reader's `whoami` and `discover_tools` were refused by the gate, so a failed pipeline's job log stayed unread
and the sweep said "cause in the log". A read is allowed by its verb; those two verbs were missing.

## What Changes

- `ReadGate` passes `whoami` and `discover*` beside the other read verbs; every write verb stays refused.
- An unreadable round names what stopped the read, on one line, instead of "cause in the log".

## Capabilities

### Modified Capabilities

- `protocol`: a read may ask who it is signed in as or which tools the host has.
