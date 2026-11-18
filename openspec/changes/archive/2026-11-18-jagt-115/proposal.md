## Why

In an organisation acceptance's scenarios, steps and flows live in a shared repository, committed. Nothing committed the
workspace, so the human did it by hand after every check (JAGT-115).

## What Changes

- `workspace.mergeRequest` in the acceptance `role.yml` (default `false`): after a check that changed a workspace inside a
  configured project, jagt's own code commits the change on `jagt/acceptance-<role>`, pushes it, and opens or updates the
  one request for that branch on the project's code host.
- `CodeHost.openRequest`: the port's first request write besides the close; the `github` and `gitlab` drivers implement it.
- AGENTS.md's "human in the loop" names this a fifth exception to "nothing else writes to the code host unasked", and
  `roles/acceptance/design.md` guards it. Decision record 0198.

## Capabilities

### Modified Capabilities

- `acceptance-workspace`: the workspace is committed through a request of jagt's own, only where `workspace.mergeRequest` is on; the base branch is never written.
