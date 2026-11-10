## Why

Permission menus, multi-select questions, a login or a stuck input need keys a typed line cannot send.

## What Changes

- The Session tab carries a row of keys that press one named key in the task's window.
- The set is fixed and validated on the server; no free key string reaches the session host.

## Impact

- Specs: `board-verbs`.
- Code: `SessionHost.Key`, `TmuxSessionHost`, `AgentSessions`, `TypedLineController`, the Session tab.
