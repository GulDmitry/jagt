## Why

A replayed run cannot check a deployed change: its QA read always answered failed, so no stand scenario reaches the scripted flows.

## What Changes

- A replayed QA read answers the verdict and flow asks its recording holds; it fails only where the recording holds none.
- A live QA read logs its answer as an `assistant read answered` event of kind `QA_CHECK`, which the stand's recorder already writes.

## Impact

- Spec: `qa`.
- `adapter/assistant/ReplayCheckerReader`, `HeadlessClaudeCheckerReader`, `HeadlessClaude`.
