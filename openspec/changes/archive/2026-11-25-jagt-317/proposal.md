## Why

A cluster with a tight ephemeral-storage quota points `TMPDIR` and `XDG_CACHE_HOME` at the volume, and tools failed on an empty one.

## What Changes

- Each start creates `TMPDIR` and `XDG_CACHE_HOME` when they lie under `ORCHESTRATOR_DATA`.
- `docs/server.md` and `examples/kubernetes.yml` name both for a quota-bound namespace.

## Capabilities

### New Capabilities

### Modified Capabilities

- `server-environment`: the temporary and cache folders on the volume exist before jagt starts.
