## Why

`kittyCommand` and `kittyFontSize` carry the viewer's vendor name at the top level of `jagt.yml`, exempted by name from
`config-key-vendors`. A vendor's name belongs only on that vendor's own block.

## What Changes

- The kitty viewer's settings sit in their own block: `kitty: {command, fontSize}`.
- `kittyCommand` and `kittyFontSize` read as aliases until release 0.32.0; startup names the new key of each one written, once.
- `config-key-vendors` exempts no key.

## Capabilities

### New Capabilities

### Modified Capabilities

- `platform`: the kitty viewer's keys.
- `session-host`: the renamed keys read as aliases.
