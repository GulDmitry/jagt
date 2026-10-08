## Why

git reads `--dele` and `-ud` as `--delete`, and both passed `ToolGate`.
`git -c remote.origin.push=refs/heads/ABC-42:refs/heads/main push origin ABC-42` updated `main`.

## What Changes

- Any prefix of `--delete`, and a short bundle holding `d`, is a delete.
- A push line carrying `-c` is refused.

## Capabilities

### Modified Capabilities

- `git`: delete and config spellings on a push line.
