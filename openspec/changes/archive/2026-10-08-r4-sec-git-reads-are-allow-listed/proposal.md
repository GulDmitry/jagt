## Why

`ReadGate` judged a git read by a path heuristic: `blame --ignore-revs-file=/abs`, `diff -O/abs` and
`ls-files --exclude-from=/abs` read a file outside the worktrees, the Master's token included. A Glob's `{../,}`
escaped too.

## What Changes

- A git read names its options from a list per subcommand; every value and path stays inside the worktrees.
- `diff`, `log`, `show` and `blame` run only with `--no-ext-diff --no-textconv`.
- A `..` after `{` or `,` in a glob is a parent.

## Capabilities

### Modified Capabilities

- `master`: the reviewer's git is allow-listed.
