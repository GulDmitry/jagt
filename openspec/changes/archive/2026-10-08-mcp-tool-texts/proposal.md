## Why

Tool texts contradicted the brief: `update_agent_status` asked for frequent keep-alive calls, `notify_user` invited a
question, `*_task_tab` tools spoke of windows, and an unknown tool name was answered with nothing to pick from.

## What Changes

- `update_agent_status` is called when the status changes; `notify_user` is never for a question.
- Tab tools say tab.
- An unknown tool is refused with the caller's own tool list.

## Capabilities

### Modified Capabilities

- `protocol`: an unknown tool's refusal names the caller's tools.
