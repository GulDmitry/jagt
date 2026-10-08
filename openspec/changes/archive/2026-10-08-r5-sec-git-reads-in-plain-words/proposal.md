## Why

A brace, a tilde or a glob in the reviewer's git line was expanded by the shell after `ReadGate` judged it:
`{/abs/secret,README}` printed a file outside the worktrees. A Glob's `.{.,}/` escaped the same way.

## What Changes

- A git read is plain words and single spaces: no quote, `~`, `^`, brace, glob or operator; `%G` runs gpg, refused.
- A Glob pattern holding a brace or `..` is refused.

## Capabilities

### Modified Capabilities

- `master`: the reviewer's git line is an allow-list of characters.
