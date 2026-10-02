# 0005 — The base branch is read-only

[← decisions](README.md) · rule: [`git.md`](../rules/git.md)

## Status

Accepted 2026-07-27 (`8e695cd`), `revert` added 2026-08-13 (`4104986`), recorded retroactively 2026-10-02.

## Context

From the first commit (`5ab120b`) `deploy` merged a task branch into a project's `deployBranch` and pushed, and
tasks were cut from `baseBranch`. A worktree branch cut from `origin/<baseBranch>` inherits it as upstream, so a
bare `git push` would push the task straight into the release branch. The Master prompt of `8e695cd` names
merging into a release branch a critical incident.

`deploy` then had no way back: a deploy that broke the deploy branch sent the human out of the tool at the worst
moment (`4104986`).

On 2026-08-26 (`58f8957`) an agent asked for a small edit read a stale ship instruction as permission, committed,
and force-pushed over work the human had already read.

## Decision

- The base branch is read-only: nothing pushes or merges to it, ever. `deployTask` refuses when the deploy
  branch equals the base branch.
- Only `deploy` and its undo `revert` write a shared branch, both Master-only through `deployTarget`.
- `ship` only opens or updates a review request; it never merges.
- `revert` reverts the merge commit `deploy` recorded and pushes that: it adds a commit, never rewrites history,
  never force-pushes. The task branch keeps its commits.
- A sub-agent pushes its own task branch and nothing else. `detachUpstream` unsets the inherited upstream at
  creation, so a bare `git push` errors instead.
- Nothing rewrites what has left the machine (`58f8957`): no `--force`, no `--force-with-lease`, no
  `commit --amend` or `reset --hard` onto a pushed commit. A pushed branch is corrected by another commit.

## Rejected

- A fast-forward deploy: without `--no-ff` and a recorded merge commit, "the deploy" is a range of loose commits
  and reverting it would undo a fraction of the task (`4104986`).
- `revert` searching the log for a deploy it has no record of: it refuses and gives the by-hand recipe instead.

## Reopen when

- A shared branch is written by anything but `deploy` or `revert`.
- A revert needs a rewrite to take a deploy back out.
