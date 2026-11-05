## Why

A local model server that is down or serves another model went unnoticed for `assistant.local`, and for `opencode` when only its model was wrong.

## What Changes

- One read covers `opencode` and `assistant.local`: an endpoint not listing its configured model is a lost reach.

## Capabilities

### Modified Capabilities

- `startup`: the endpoint read names the model too, for both local endpoints.
