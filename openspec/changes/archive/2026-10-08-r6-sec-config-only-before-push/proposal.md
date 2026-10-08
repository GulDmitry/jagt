## Why

`ToolGate` refused a `-c` anywhere on a line holding a push: `git commit -c HEAD && git push origin ABC-42` was
refused.

## What Changes

- Only a `-c` among the push's own git options, before `push`, refuses it.

## Capabilities

### Modified Capabilities

- `git`: a config refuses a push only where it configures that push.
