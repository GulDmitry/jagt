## Why
Everything jagt writes sat in `~/.jagt`, away from the folder jagt runs from.
An older install's files lay loose at the root behind many ignore lines.

## What Changes
- The data folder defaults to `data/` under the root, one gitignored line, with a tree: `jagt/`, `projects/`, `tasks/<task>/`, `archive/`, `records/`, `memory/`, `logs/`.
- At start, what an older version kept at the root, in `~/.jagt` or flat in the data folder moves to its place, logged per file, never over an existing file, nothing deleted.
