## Why

`git send-pack <remote> refs/heads/x:refs/heads/main` updated `main` with no pre-push hook and passed `ToolGate`;
`http-push` and a `gh`/`glab api` write to a ref did too.

## What Changes

- `ToolGate` refuses `send-pack`, `http-push`, `receive-pack`, and a `gh`/`glab api` call with a method other than
  GET or with a field or body.

## Capabilities

### Modified Capabilities

- `git`: `ToolGate` refuses a write to the code host past the push.
