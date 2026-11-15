## Why

`platform` defaulted to macOS, so a Linux machine had to name itself; the machine already knows.

## What Changes

- The platform is the OS jagt runs on; `platform` in `jagt.yml` only overrides it.
- A `platform` naming another OS than the machine's refuses the start, naming both.

## Capabilities

### Modified Capabilities

- `startup`: 'Linux with the platform unset' starts with the Linux drivers; a mismatching `platform` names both.
