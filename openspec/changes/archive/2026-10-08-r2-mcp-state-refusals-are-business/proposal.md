## Why

A call the state refused — the task cap, a name already registered, a report its status does not allow — came back
as `validation`, telling the session to fix its fields and resend a call that cannot pass.

## What Changes

- A refusal by what jagt already holds is a `flow/Refusal` and comes back as `business`; a wrong argument stays
  `validation`.

## Capabilities

### Modified Capabilities

- `protocol`: a state refusal is `business`.
