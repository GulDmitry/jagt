## Why

An acceptance run with no ticket text and no deploy reader ended ACCEPTANCE_FAILED, though the change was never judged.

## What Changes

- The acceptance brief carries the ticket text jagt kept at intake.
- A round that cannot obtain its ticket or its deploy stops on `ACCEPTANCE_UNREAD`, a read-ended stop naming the input, not on `ACCEPTANCE_FAILED`.

## Capabilities

### Modified Capabilities

- `acceptance-workspace`: what an acceptance round is given, and the read-ended stop where it lacks an input.
