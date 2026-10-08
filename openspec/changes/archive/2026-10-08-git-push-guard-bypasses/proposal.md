## Why

`ToolGate` let through pushes that switch the pre-push hook off (`--no-verify`, `-c core.hooksPath`, an inline
alias, `GIT_CONFIG_*` overrides, `env -i`, `sh -c`) and a `HEAD` push after `git switch main`.

## What Changes

- `ToolGate` refuses a push line carrying any of those, and a `HEAD` push on a line that may move off the task's
  branch or worktree.
- It stays a guardrail: a push built at runtime, a configured alias or a script still passes.

## Capabilities

### Modified Capabilities

- `git`: the push guard names everything it refuses and what passes it.
