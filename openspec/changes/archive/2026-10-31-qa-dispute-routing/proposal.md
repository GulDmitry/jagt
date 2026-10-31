## Why

QA's `decline` and dispute were written in the cases and the decision, and nothing read a session's no-change reply back to QA.

## What Changes

- A session's `no_changes` to a `qa-round` is read back to QA (`QaDeclineJob`): it accepts, or disputes.
- A first dispute is a question on the DEPLOYED card, answered by the Master in `act` unless the human keeps answering;
  a second is one only the human answers; nothing is read after it.

## Capabilities

### Modified Capabilities

- `qa`: the decline read and the dispute's route.
