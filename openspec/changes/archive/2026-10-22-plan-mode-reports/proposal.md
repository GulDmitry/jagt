## Why

A session whose client lets through only calls that change nothing could not report its status or its question, so it blocked unseen.

## What Changes

- Every MCP tool declares an `Effect`; `tools/list` carries it as `readOnlyHint`.
- A read, or a report to jagt, is `REPORTS`.

## Capabilities

### New Capabilities

### Modified Capabilities

- `protocol`: a tool says whether it changes anything.
