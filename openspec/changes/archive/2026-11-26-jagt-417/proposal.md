## Why

A model's verdict, press and row status crossed the port as Strings: an unknown word was decided unnamed, by `equals("ready")`
or an `orElse` into UNPROVABLE.

## What Changes

- Verdict, press and row status are types resolved in `protocol/`; no String component of that name in `port/assistant`.
- A word outside the vocabulary is a named violation, asked once more, then a failed answer naming it on the card.

## Capabilities

### Modified Capabilities

- `protocol`: a paid read's violations include an answer's unknown verdict or row status.
