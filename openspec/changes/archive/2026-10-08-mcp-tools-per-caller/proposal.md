## Why

A sub-agent was shown `open_task_tab` (which killed its own window), `close_task_tab`, `focus_task`,
`write_task_context` (overwriting the human's commit approval) and `list_tasks` (every task, every project).

## What Changes

- Those five tools are the Master's: a sub-agent is neither shown them nor answered.
- A sub-agent keeps `update_agent_status`, `notify_user` and `open_in_ide`.

## Capabilities

### Modified Capabilities

- `protocol`: which tools each caller sees.
