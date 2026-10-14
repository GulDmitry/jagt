## Why

`stats` splits a task's time between the human, the agents and the host, but only while it is open; once `done`
retires it, nothing says whether the human's share is shrinking.

## What Changes

- `finished` ends with the median time a retired task took and its held time split by owner, from the status log.
- The split is charged by one rule for open and retired tasks alike.

## Capabilities

### Modified Capabilities

- `board`: the `finished` report.
