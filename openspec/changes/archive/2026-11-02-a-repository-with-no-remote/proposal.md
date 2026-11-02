## Why

`origin` was written into every fetch, push, ref and merge, so a repository kept on this machine, or one whose remote has
another name, could not be worked on. Every third party is a configured seam ([0130](../../../docs/decisions/0130-every-third-party-is-a-configured-seam.md)).

## What Changes

- A project's `remote` names the git remote its branches are shared through: `origin` by default, `none` for a repository kept on this machine.
- With `none`, worktrees are cut from the local base branch, nothing is fetched or pushed, and a deploy merges into the local deploy branch.
- Startup refuses a remote the repository does not list, and `none` beside a code host that is not `none`.

## Capabilities

### Modified Capabilities

- `task-start`: the project's remote and the repository kept on this machine.
- `deploy-check`: no remote means no pipeline to wait for.
- `git`: a ship with no remote pushes nothing.
