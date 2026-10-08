## Why

The startup spec refused `deploy` without `revert` in every mode; `MasterCheck` refuses it only in `act`.

## What Changes

- The startup requirement says `deploy` and `revert` are both or neither in `act`.

## Capabilities

### Modified Capabilities

- `startup`: the deploy-and-revert check names the mode it holds in.
