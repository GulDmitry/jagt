## Why

An OpenCode read was held to its scope only after the run, so one refused call discarded the whole answer.

## What Changes

- Every call of an OpenCode read goes to the read gate before it runs; a refused call is denied and the model retries.
- The after-the-run re-check of shell, fetch and git calls goes; only the role's refused files are still read back.

## Capabilities

### Modified Capabilities

- `startup`: a read on OpenCode is gated per call.
