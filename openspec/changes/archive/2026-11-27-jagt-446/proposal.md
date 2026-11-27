## Why

A render that threw while painting the state reached the human as "Cannot reach the backend", so the board looked down.

## What Changes

- A failed read of the state and a failed render each get their own toast; the render one names its cause.

## Capabilities

### Modified Capabilities

- `board-app`: gains a requirement telling a failed read from a failed render.
