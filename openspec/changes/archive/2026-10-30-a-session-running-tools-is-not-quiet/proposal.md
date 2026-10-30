## Why

A session that only ran tools, with no MCP call and no hook, was called quiet once the threshold passed.

## What Changes

- The PreToolUse gate signs the session used a tool, throttled as the MCP keep-alive is; it ends a halt like a call and is no report.

## Capabilities

### Modified Capabilities

- `attention`: a tool use is a sign of life.
