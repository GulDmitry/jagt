## Why

The fence's hook comes in through `--settings`; a managed `allowManagedHooksOnly` policy drops it without a word, and
the run fell back to the human's own allow rules.

## What Changes

- The schema's own `StructuredOutput` call passes the hook, so a run returning that object while no call reached
  `ReadGate` ran unfenced: its answer is discarded and logged.

## Capabilities

### Modified Capabilities

- `protocol`: an answer whose run never reached the gate is discarded.
