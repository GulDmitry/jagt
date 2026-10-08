## Why

`ToolGate` let `GIT_DIR=…`/`GIT_WORK_TREE=…` keep `HEAD` the task's, took `ABC-42:HEAD` for the task's branch, and
read `\#x` as a comment and `x\>` as a redirection, hiding the `main` after them.

## What Changes

- `GIT_DIR` and `GIT_WORK_TREE` move `HEAD`; only a bare `HEAD` is the task's branch; an escaped character is data.

## Capabilities

### Modified Capabilities

- `git`: `ToolGate` reads `HEAD` and escapes as the shell does.
