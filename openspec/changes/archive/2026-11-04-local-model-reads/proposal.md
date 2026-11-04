## Why

Mapping a line of text onto a command reads no tool, yet costs a cloud round trip.

## What Changes

- A read of a kind that reads no tool may ask a local OpenAI-compatible model, set in `jagt.yml` under `assistant.local`, off by default.
- A failed local read is asked of the cloud once.
- A misconfigured local model refuses the start naming its key.

## Capabilities

### Modified Capabilities

- `startup`: a read that needs no tool may ask a local model.
