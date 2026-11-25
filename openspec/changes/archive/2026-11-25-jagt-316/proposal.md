## Why

A new image's `jagt.yml` never reached a volume that already held one, so a changed key needed a wrapped entrypoint.

## What Changes

- `JAGT_CONFIG_FROM_IMAGE=true` copies the image's file over the volume's at every start; unset or false keeps the copy-once.
- `docs/server.md` names the option.

## Capabilities

### New Capabilities

### Modified Capabilities

- `server-environment`: the configuration on the volume may be replaced from the image at every start.
