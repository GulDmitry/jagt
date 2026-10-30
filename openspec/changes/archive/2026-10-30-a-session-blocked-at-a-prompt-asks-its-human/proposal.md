## Why

A permission prompt the hook reported turned the card to "agent stopped" while the report stayed plain, so the board did not say the session waits for an answer.

## What Changes

- A session blocked at a prompt, by its hook or at launch by its window, asks: NEEDS INPUT, answer the session, no stopped banner.
- Work starting again clears a permission a prompt raised; one the session said stays.

## Capabilities

### Modified Capabilities

- `attention`: a blocked prompt is a permission ask, not a silence.
