## Why

A reviewer bot conceded to the agent's push-back, the thread was relayed every poll, the Master shipped nothing three
times and the loop breaker stopped the task. The dedup compared model-copied text, which differs on every read.

## What Changes

- A review read carries each open thread's newest note id.
- A round with the same open threads and newest notes, checks not red, is not relayed again.
- The loop breaker counts rounds and ships that brought no new note, not threads that did not drop.

## Capabilities

### Modified Capabilities

- `review`: an unchanged round is judged by its notes.
- `unattended-work`: the loop breaker measures new notes.
