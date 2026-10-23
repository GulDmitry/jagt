## Why

A session needing a second repository cut its worktree with `git worktree add` itself, so jagt never learned of it
and `ship` handled the first repository only. The `add-repo` verb is the path; nothing refused the bypass.

## What Changes

- `ToolGate` refuses `git … worktree add …`; the refusal names `update_agent_status` `outcome=question` with `needsProject`.
- Other `git worktree` subcommands stay allowed.

## Impact

- Spec: guards. Code: `ToolGate`.
