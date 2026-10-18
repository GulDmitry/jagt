## Why

`finished.jsonl` and `artifacts/` only warned past their limit, the Master's log was never emptied, and every headless
read left a transcript behind.

## What Changes

- Every store jagt writes has a ceiling and an action past it: pack the older half, refuse, roll, or keep nothing.

## Capabilities

### Modified Capabilities

- `guards`: a store jagt writes has a ceiling.
