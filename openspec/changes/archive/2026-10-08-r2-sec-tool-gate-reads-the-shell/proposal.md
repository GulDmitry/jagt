## Why

`ToolGate` passed `git push origin ABC-42 & git push origin main`, a branch after a redirection, `\git`, `"git"`,
`GIT_CONF''IG_COUNT`, `env -` and a plain `--force` of the task's own branch, which 0022 forbids.

## What Changes

- A single `&` ends a command; a redirection is skipped with its target, not the end of the line.
- Backslashes and empty quote pairs are dropped before matching; a word quoted whole is still `git`.
- `--force`, `-f` and a `+` refspec are refused; the task's branch goes under `--force-with-lease`.

## Capabilities

### Modified Capabilities

- `git`: the gate refuses a forced push and reads the line as the shell does.
