## Why

`keepAwake: true` on Linux held nothing, so an unattended run there slept while the key said it would not.

## What Changes

- On Linux the hold is `systemd-inhibit --what=idle:sleep`, around a `tail --pid` that ends with jagt.

## Capabilities

### Modified Capabilities

- `platform`: the machine stays awake on Linux too.
