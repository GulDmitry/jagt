## Why

What changed a task, when and by whom is recorded in every task's status log, live or finished; the activity report showed
only a log tail that said none of it.

## What Changes

- `activity` lists every status change of live and finished tasks, newest first, filtered by kind and project.
- A line naming a task still on the board opens it.
- The log-tail content of `activity` is gone.

## Impact

- Spec: `unattended-work`.
- `ActivityReport`, `ActivityCommand`, `ActivityKind`, the report's task link.
