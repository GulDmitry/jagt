## Why

A project added from the board was written to the image's `/app/jagt.yml` and lost on a recreate; a copy the operator made had no effect.

## What Changes

- The image reads and writes `jagt.yml` on the volume, `/data/jagt.yml`, seeded from the image's copy on a first start.

## Capabilities

### New Capabilities

### Modified Capabilities

- `server-environment`: where the image keeps `jagt.yml`.
