## Why

The ids of the steps jagt sends were string literals in thirty files, composed with `+`; a step added in code could
reach a session with no brief section or no case, and the first relay was a miss.

## What Changes

- One `Step` enum holds every step id; the relays, the brief and the wake take it, the wire strings stay as they are.
- Startup refuses a `Step` the brief holds no section for, or no case sends.

## Capabilities

### New Capabilities

### Modified Capabilities

- `cases`: startup checks every step jagt can send against the brief and the cases.
