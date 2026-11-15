## Why

A server holds no repository a human cloned: a project must be nameable by its url, and a fresh clone holds none of the gitignored files a worktree needs.

## What Changes

- A project names `url` and optionally `path`; a path that exists is used as is, otherwise the url is cloned into `repos/<project>` under the data folder at start.
- A clone that fails is one mark on the board's head naming the project and the cause; the other projects work, and the clone is tried again each minute.
- A project's `localFiles` is the folder `worktree.copyGlobs` copies from; default the project's path.

## Impact

- `project-setup`: gains "A project named by url is cloned".
- `task-start`: gains "A worktree's local files come from the project's local folder".
