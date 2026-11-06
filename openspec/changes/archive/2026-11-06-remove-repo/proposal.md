## Why

A repository attached by mistake, or one a plan no longer needs, stayed on the task until its request was closed on the
host by hand.

## What Changes

- `remove-repo <ticket> <project>`, human-only and typed with no button, closes that repository's request on its code host
  and takes the repository out of the task as a host-closed one leaves.
- A project not on the task, the primary repository and one already deployed are refused with nothing changed.
- Closing a request is a new operation of the code-host seam: `gh pr close`, `glab mr close`, a `closeCommand`.

## Capabilities

### Modified Capabilities

- `repo-attach`: a running task loses a repository.
- `board`: remove-repo is typed with a project, with no button.
