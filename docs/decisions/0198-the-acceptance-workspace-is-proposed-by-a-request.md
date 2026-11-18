# 0198 — The acceptance workspace is proposed by a request

[← decisions](README.md) · on [0158](0158-acceptance-keeps-its-checks-in-a-workspace.md),
[0130](0130-every-third-party-is-a-configured-seam.md)

## Status

Accepted 2026-11-18, owner's choice (JAGT-115).

## Context

- In an organisation acceptance's scenarios, steps and flows live in a shared repository, committed; nothing committed them.
- A role branch is no task branch: no session owns it, so the `ship` instruction to an agent has no one to reach.
- AGENTS.md allowed four writes to the code host unasked.

## Decision

- A fifth exception to "nothing else writes to the code host unasked": the acceptance role's workspace request.
- `workspace.mergeRequest` in `roles/acceptance/role.yml` turns it on; the default is `false`.
- After a check that answered, with a workspace inside a project of `jagt.yml`, jagt's own code (`WorkspaceRequests`) commits the
  files git does not ignore on `jagt/acceptance-<role>` and pushes that branch alone.
- The content is the workspace folder as the check left it in the project's working tree; a symbolic link is skipped, never followed.
- The commit is cut in a detached checkout of its own, from the branch's remote tip while its request is open, else the base branch:
  the human's checkout and local branches are never touched, and the push is never forced.
- `CodeHost.openRequest` then opens the one request for the branch, or returns the open one: at most one per role, updated by later pushes.
- The base branch is never written, nor a branch named like it or the deploy branch.
- `remote: none` gets nothing, `codeHost: none` the push alone; `command` hosts open no request.
- A failure is logged and the check's verdict stands; the next check tries again.

## Rejected

- The role committing itself: it holds no push and no credential (`acceptance` design).
- A request opened by the Master or a session: a role branch has neither.
- A force-push to refresh the branch: [0022](0022-the-resume-rebase-is-the-one-rewrite-and-the-guard-is-a-guardrail.md) allows none here.
- On by default: an install with the workspace in its own folder has no repository to propose to.

## Reopens when

- A second role keeps a workspace.
- A host other than `github` and `gitlab` is asked to open requests.
