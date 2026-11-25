## Why

A session stopped to ask the human whether a prerequisite ticket was closed and which of the ticket's own two routes to take; the Master in `act` should have answered both.

## What Changes

- The Master's answer quotes the tracker's own status for every other item the question names, read by jagt before the model is asked.
- Where the tracker cannot be read for a named item, the question waits for the human.
- The Master picks one of the ticket's own routes, the one needing no right the session lacks, and says why.

## Capabilities

### New Capabilities

### Modified Capabilities

- `master`: a question naming a ticket is answered from the tracker; a question between the ticket's routes is decided.
