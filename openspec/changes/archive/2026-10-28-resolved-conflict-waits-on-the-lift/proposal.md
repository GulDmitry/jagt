## Why

A resolved deploy conflict on a stopped task logged `master finishes deploy` every tick and pressed a DEPLOY the stop guard refused: the log claimed work nothing did.

## What Changes

- The finish is not pressed while the task is stopped; one INFO line says the resolution waits on the stop, once per stop.
- A press is logged with its result, a refusal with its cause.

## Capabilities

### Modified Capabilities

- `task-flow`: the deploy-conflict requirement gains a scenario.
