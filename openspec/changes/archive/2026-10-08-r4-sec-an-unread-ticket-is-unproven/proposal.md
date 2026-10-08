## Why

Where jagt quoted no ticket, the reviewer was told to read it with its tracker tools, but it loads no MCP server
unless `assistant.allowedTools` names one, the default.

## What Changes

- With no server loaded, the review and the plan read are told the ticket was not read: they rule on the diff or
  the plan, and a premise resting on the ticket is unproven.

## Capabilities

### Modified Capabilities

- `master`: an unread ticket is said, never sent for.
