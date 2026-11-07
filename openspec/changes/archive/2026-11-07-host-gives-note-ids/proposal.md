## Why
A model wrote the note ids a round is compared by, and they differed between two reads of one request, so a repeat round was relayed again.

## What Changes
- The note ids of a review round come from the code host's own read, one per code-host driver, never from the model.
- The review read no longer asks the model for them.
- A round says how many notes are new or unchanged since the last, not which thread.

## Capabilities

### Modified Capabilities

- `review`: an unchanged round is recognised by the host's note ids.
