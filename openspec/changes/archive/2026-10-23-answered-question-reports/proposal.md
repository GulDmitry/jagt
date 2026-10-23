## Why

A session asked, the human answered in its terminal, and it worked on for hours without reporting again: the question
it had put exempted every later turn end, so the card said NEEDS INPUT the whole time.

## What Changes

- A question no longer exempts a turn end; only a report since the turn began does (`Move.endsUnreported`).

## Capabilities

### New Capabilities

### Modified Capabilities

- `attention`: a turn ending on a question an earlier turn put is sent back to report first.
