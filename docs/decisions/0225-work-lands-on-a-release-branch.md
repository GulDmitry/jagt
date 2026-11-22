# 0225 — Work lands on a release branch, and merging its request is the release

[← decisions](README.md) · rule: [`AGENTS.md`](../../AGENTS.md) "Git and committing" · amends:
[0223](0223-main-moves-by-a-green-pull-request.md)

## Status

Accepted 2026-11-22 by the owner.

## Context

- Under 0223 every batch needed a branch of its own, named for content nobody knew in advance.
- A squash merge folded a batch's features into one commit and left the local base diverged from the remote.

## Decision

- Work lands on one fixed branch, `release`; humans and sessions both work from it, pushed after each green batch.
- One standing pull request, `release` into `main`; its checks run on every push.
- Merging it with a merge commit is the release; `release` then fast-forwards to `main`, so nothing is synced by hand.
- `main` therefore equals the last release.
- A hotfix is a published release broken for its users (install, upgrade, start, data, security) while `release`
  cannot ship; the owner labels its bug task `hotfix`.
- A hotfix branches `hotfix/<task>` from `main`; its fix and regression test alone, merged by a pull request, is the patch
  release; `main` is merged into `release` the same day, and the branch is deleted.

## Rejected

- A versioned name (`release/0.26.0`): the version is chosen at release, after the branch is cut.
- Squash merges: one commit per feature is lost, and `release` no longer fast-forwards.

## Reopen when

- Two released versions need fixes at once.
