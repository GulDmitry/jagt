## Why

Headless reads had no protection against a model-provider outage: every job called on every tick, burning time and log
lines, and nothing on the board said the assistant was down (decision 0085).

## What Changes

- After `assistant.breakerFailures` consecutive provider failures, headless reads stop for `assistant.breakerCooldown`;
  one probe then closes the breaker on success or re-opens it on failure.
- A refused read fails as `AssistantUnavailable`, naming the last provider error.
- The header chip `.chip.bad` gains a second use, `assistant unavailable`; it replaces nothing and adds no mark.

## Capabilities

### New Capabilities

### Modified Capabilities

- `usage-limits`: headless reads stop after repeated provider failures, and the header says so.
